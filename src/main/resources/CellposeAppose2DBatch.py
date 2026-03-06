import os
import appose
import re
from cellpose import models, io
import numpy as np
import logging
from pathlib import Path


# Define a custom logging handler that sends log messages to a function
class FunctionHandler(logging.Handler):
    def __init__(self, func):
        super().__init__()
        self.func = func
    
    def emit(self, record):
        log_message = self.format(record)
        self.func(log_message)


def appose_log_function(message):
    # Parse progress if present
    progress_match = re.search(r'(\d+)%\|[^|]+\|\s*(\d+)/(\d+)', message)
    if progress_match:
        percentage = int(progress_match.group(1))
        current = int(progress_match.group(2))
        total = int(progress_match.group(3))
        task.update(None, current, total)
    else:
        task.update(message)


def run_cellpose_on_volume(model, img, axes, params, logger):
    """
    Run Cellpose on a single 3D/4D volume (no time dimension).
    
    Parameters:
        model: Cellpose model
        img: numpy array (3D or 4D: ZYX, CZYX, or similar)
        axes: dict mapping axis names to indices
        params: dict with Cellpose parameters
        logger: logging.Logger instance
    
    Returns:
        masks: numpy array of segmentation masks
    """
    z_axis = axes.get('Z', None)
    c_axis = axes.get('C', None)
    
    # Build eval parameters
    eval_params = {
        'diameter': params['diameter'],
        'channels': params['channels'],
        'progress': True,
        'flow_threshold': params['flow_threshold'],
        'cellprob_threshold': params['cellprob_threshold'],
        'min_size': params['min_size'],
        'do_3D': params['do_3D'],
        'stitch_threshold': params['stitch_threshold'],
        'anisotropy': params['anisotropy']
    }
    
    # Add axis specifications
    if z_axis is not None:
        eval_params['z_axis'] = z_axis
    if c_axis is not None:
        eval_params['channel_axis'] = c_axis
    
    logger.info(f"Running on shape {img.shape} with axes {axes}")
    logger.info(f"Cellpose params: {eval_params}")
    
    out = model.eval(img, **eval_params)
    return out[0]


def process(img, axes):
    # Create log in USER_HOME/.icy/
    user_home = Path.home()
    icy_dir = user_home / '.icy'
    icy_dir.mkdir(exist_ok=True)
    log_file = icy_dir / 'cellpose_debug.log'
    
    # Set up file logging
    file_handler = logging.FileHandler(str(log_file), mode='w')
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s'))
    
    # Set up custom function handler for appose
    function_handler = FunctionHandler(appose_log_function)
    function_handler.setLevel(logging.INFO)
    function_handler.setFormatter(logging.Formatter('%(levelname)s - %(message)s'))
    
    # Configure cellpose logger
    cellpose_logger = logging.getLogger('cellpose')
    cellpose_logger.handlers.clear()
    cellpose_logger.setLevel(logging.DEBUG)
    cellpose_logger.addHandler(file_handler)
    cellpose_logger.addHandler(function_handler)
    cellpose_logger.propagate = False
    
    # Silence numba debug spam
    for logger_name in ['numba', 'numba.core', 'numba.core.byteflow', 
                        'numba.core.interpreter', 'numba.core.ssa']:
        logging.getLogger(logger_name).setLevel(logging.WARNING)
    
    cellpose_logger.info(f"Logging to: {log_file}")
    cellpose_logger.info(f"Input image shape: {img.shape}")
    cellpose_logger.info(f"Axes mapping: {axes}")
    
    # Detect dimensions
    t_axis = axes.get('T', None)
    z_axis = axes.get('Z', None)
    c_axis = axes.get('C', None)
    
    has_time = t_axis is not None and img.shape[t_axis] > 1
    has_z = z_axis is not None and img.shape[z_axis] > 1
    
    cellpose_logger.info(f"Time axis: {t_axis} (n={img.shape[t_axis] if has_time else 0})")
    cellpose_logger.info(f"Z axis: {z_axis} (n={img.shape[z_axis] if has_z else 0})")
    cellpose_logger.info(f"C axis: {c_axis}")
    
    # Store parameters
    params = {
        'model_name': '${--pretrained_model}',
        'use_gpu': ${--use_gpu},
        'diameter': ${--diameter},
        'channels': [${--chan}, ${--chan2}],
        'flow_threshold': ${--flow_threshold},
        'cellprob_threshold': ${--cellprob_threshold},
        'min_size': ${--min_size},
        'do_3D': ${--do_3D},
        'stitch_threshold': ${--stitch_threshold},
        'anisotropy': ${--anisotropy}
    }
    
    # Monkey-patch size_model_path
    original_size_model_path = models.size_model_path
    def patched_size_model_path(model_type):
        try:
            return original_size_model_path(model_type)
        except FileNotFoundError:
            return None
    models.size_model_path = patched_size_model_path
    
    # Create model
    cellpose_logger.info("Creating Cellpose model...")
    model = models.Cellpose(model_type=params['model_name'], gpu=params['use_gpu'])
    
    # CASE 1: 5D with both Z and T → Loop over time
    if has_time and has_z:
        n_timepoints = img.shape[t_axis]
        cellpose_logger.info(f"5D image detected (XYCZT). Processing {n_timepoints} timepoints separately.")
        
        masks_list = []
        for t in range(n_timepoints):
            cellpose_logger.info(f"Processing timepoint {t+1}/{n_timepoints}")
            
            # Extract single timepoint
            indices = [slice(None)] * img.ndim
            indices[t_axis] = t
            img_t = img[tuple(indices)]
            
            # Adjust axes (remove T, shift indices after T)
            axes_t = {}
            for axis_name, axis_idx in axes.items():
                if axis_name != 'T':
                    new_idx = axis_idx if axis_idx < t_axis else axis_idx - 1
                    axes_t[axis_name] = new_idx
            
            # Process this timepoint
            masks_t = run_cellpose_on_volume(model, img_t, axes_t, params, cellpose_logger)
            masks_list.append(masks_t)
            
            # Update progress
            task.update(None, t+1, n_timepoints)
        
        # Stack results
        masks = np.stack(masks_list, axis=0)
        cellpose_logger.info(f"Stacked {len(masks_list)} timepoints. Final shape: {masks.shape}")
    
    # CASE 2: 4D or less (XYCT, XYCZ, XYC, etc.) → Process directly
    else:
        cellpose_logger.info("4D or less. Processing entire volume with Cellpose batching.")
        masks = run_cellpose_on_volume(model, img, axes, params, cellpose_logger)
        cellpose_logger.info(f"Output mask shape: {masks.shape}")
    
    # Close handlers
    file_handler.close()
    
    return masks


# Main execution
input = image.ndarray()
masks = process(input, axes)

shared = appose.NDArray(str(masks.dtype), masks.shape)
shared.ndarray()[:] = masks[:]
task.outputs['masks'] = shared
