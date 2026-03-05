from cellpose import models, io
import numpy as np
import appose

def process(img, axes):

    io.logger_setup()

    # Monkey-patch the size_model_path function to handle missing size models
    original_size_model_path = models.size_model_path    
    def patched_size_model_path(model_type):
        try:
            return original_size_model_path(model_type)
        except FileNotFoundError:
            # Return None if size model not found
            return None
    models.size_model_path = patched_size_model_path

    model = models.Cellpose(model_type='${--pretrained_model}', gpu=${--use_gpu})

    task.update(f"Original image shape: {img.shape}")
    task.update(f"Axes mapping: " + str(axes))
    # task.update(f"Reshaped image shape: {image_reshaped.shape}")

    # Run Cellpose on each timepoint
    if 'C' in axes:
        caxis = axes['C']
        task.update(f"Using channel axis: {caxis}")
        out = model.eval(img, diameter=${--diameter}, channels=[${--chan},${--chan2}], channel_axis=caxis, progress=True)
    else:
        out = model.eval(img, diameter=${--diameter}, channels=[${--chan},${--chan2}], progress=True)	
    masks = out[0]
    task.update(f"Mask shape: {masks.shape}")
    return masks


input = image.ndarray()
masks = process(input, axes)  

shared = appose.NDArray(str(masks.dtype), masks.shape)
shared.ndarray()[:] = masks[:]
task.outputs['masks'] = shared
