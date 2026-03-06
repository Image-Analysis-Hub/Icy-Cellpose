import os
import re
from cellpose import models, io
import logging
import appose
from pathlib import Path

# Define a custom logging handler that sends log messages to a function
class FunctionHandler(logging.Handler):
    def __init__(self, func):
        super().__init__()
        self.func = func
    
    def emit(self, record):
        # Format the log record and send to the function
        log_message = self.format(record)
        self.func(log_message)

# Forward to Appose's logging function
def appose_log_function(message):
    # Parse progress if present
    progress_match = re.search(r'(\d+)%\|[^|]+\|\s*(\d+)/(\d+)', message)
    if progress_match:
        percentage = int(progress_match.group(1))
        current = int(progress_match.group(2))
        total = int(progress_match.group(3))
        task.update(f"Running Cellpose 3 - {percentage}%", current, total)
    else:
        task.update(message)

def process(img, axes):
    # Create log in USER_HOME/.icy/
    user_home = Path.home()
    icy_dir = user_home / '.icy'
    icy_dir.mkdir(exist_ok=True)
    log_file = icy_dir / 'cellpose_debug.log'
    
    # Set up file logging (this will catch EVERYTHING)
    file_handler = logging.FileHandler(str(log_file), mode='w')
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s'))
    
    # Set up custom function handler for appose
    function_handler = FunctionHandler(appose_log_function)
    function_handler.setLevel(logging.INFO)
    function_handler.setFormatter(logging.Formatter('%(levelname)s - %(message)s'))
    
    # Configure cellpose logger BEFORE any cellpose operations
    cellpose_logger = logging.getLogger('cellpose')
    cellpose_logger.handlers.clear()  # Clear any existing handlers
    cellpose_logger.setLevel(logging.DEBUG)
    cellpose_logger.addHandler(file_handler)
    cellpose_logger.addHandler(function_handler)
    cellpose_logger.propagate = False  # Don't propagate to root logger
    
    cellpose_logger.info(f"Logging to: {log_file}")
    
    # Store template parameters in python variables.
    model_name = '${--pretrained_model}'
    use_gpu = ${--use_gpu}
    diameter = ${--diameter}
    chan = ${--chan}
    chan2 = ${--chan2}
    flow_threshold = ${--flow_threshold}
    cellprob_threshold = ${--cellprob_threshold}
    min_size = ${--min_size}    

    # Monkey-patch the size_model_path function to handle missing size models
    original_size_model_path = models.size_model_path    
    def patched_size_model_path(model_type):
        try:
            return original_size_model_path(model_type)
        except FileNotFoundError:
            # Return None if size model not found
            return None
    models.size_model_path = patched_size_model_path

    # Log before model creation
    cellpose_logger.info("Creating Cellpose model...")
    
    model = models.Cellpose(model_type=model_name, gpu=use_gpu)
    
    import inspect
    eval_params = inspect.signature(model.eval).parameters
    cellpose_logger.info(f"model.eval() accepts these parameters: {list(eval_params.keys())}")

    cellpose_logger.info(f"Original image shape: {img.shape}")
    cellpose_logger.info(f"Axes mapping: " + str(axes))
    cellpose_logger.info(f"Using the following parameters: diameter={diameter}, chan={chan}, chan2={chan2}, pretrained_model={model_name}, use_gpu={use_gpu}")
    
    # Run Cellpose on each timepoint
    if 'C' in axes:
        caxis = axes['C']
        task.update(f"Using channel axis: {caxis}")
        out = model.eval(img, diameter=diameter, channels=[chan,chan2], 
            channel_axis=caxis, progress=True,
            flow_threshold=flow_threshold,
            cellprob_threshold=cellprob_threshold,
            min_size=min_size)
    else:
        out = model.eval(img, diameter=diameter, channels=[chan,chan2], 
            progress=True,
            flow_threshold=flow_threshold,
            cellprob_threshold=cellprob_threshold,
            min_size=min_size)
        
    masks = out[0]
    cellpose_logger.info(f"Mask shape: {masks.shape}")
    
    # Close handlers
    file_handler.close()
    
    return masks


input = image.ndarray()
masks = process(input, axes)  

shared = appose.NDArray(str(masks.dtype), masks.shape)
shared.ndarray()[:] = masks[:]
task.outputs['masks'] = shared
