import tensorflow as tf
from keras.preprocessing import image
from classification_models.tfkeras import Classifiers
from skimage import morphology
import numpy as np
from scipy.special import softmax
from skimage.measure import label, regionprops

batch_size = 1
input_shape=(640,640)


ResNet18, preprocess_input = Classifiers.get('resnet18')
base_model=ResNet18((input_shape[0],input_shape[1],3),weights='imagenet',include_top=False)

layers_name=["stage2_unit1_relu1","stage3_unit1_relu1","stage4_unit1_relu1","relu1"]
layers =[base_model.get_layer(name).output for name in layers_name]

# Diferencia 1: En lugar de tomar directamente los outputs del modelo base,
# crea un mismo modelo con la misma entrada de datos y de forma explícita
# declara como outputs las salidas de las capas que queremos coger en lugar de
# cogerlas y ya.
down_stack=tf.keras.Model(inputs=base_model.input,outputs=layers)
down_stack.trainable=False

class fast_attention_layer(tf.keras.layers.Layer):
    """docstring for multiply."""

    def __init__(self,batch_size,input_shapeQK,input_shapeV,**kwargs):
        super(fast_attention_layer, self).__init__(**kwargs)
        self.batch_size=batch_size
        self.input_shapeQK=input_shapeQK
        self.input_shapeV=input_shapeV
        self.trainable=False

    def call(self,input):
        filaQK=self.input_shapeQK[0]*self.input_shapeQK[1]
        filaV=self.input_shapeV[0]*self.input_shapeV[1]

        matrizQ=tf.reshape(input[0],[self.batch_size,filaQK,self.input_shapeQK[2]])
        matrizK=tf.reshape(input[1],[self.batch_size,filaQK,self.input_shapeQK[2]])
        matrizV=tf.reshape(input[2],[self.batch_size,filaV,self.input_shapeV[2]])

        matrizKV=tf.matmul(matrizK,matrizV,transpose_a=True,transpose_b=False)

        attention=tf.matmul(matrizQ,matrizKV,transpose_a=False,transpose_b=False)/(self.input_shapeQK[0]+self.input_shapeQK[1])

        return tf.reshape(attention,[self.batch_size,self.input_shapeQK[0],self.input_shapeQK[1],self.input_shapeV[2]])

def FastAttention(inputs,filters,batch_size,input_shape,channels_attention):
    #inputs=keras.Input(shape=input_shape)

    q=tf.keras.layers.Conv2D(channels_attention,1)(inputs) # Aquí vale 32 según el paper
    q_norm = tf.keras.layers.LayerNormalization()(q)

    k=tf.keras.layers.Conv2D(channels_attention,1)(inputs)
    k_norm = tf.keras.layers.LayerNormalization()(k) # Aquí vale 32 según el paper

    v=tf.keras.layers.Conv2D(filters,1,activation='relu')(inputs)

    #dot_kv=layers.multiply([k_norm,v])
    #dot_qkv=layers.multiply([q_norm,dot_kv])
    dot_qkv=fast_attention_layer(batch_size,(input_shape[1],input_shape[2],channels_attention),(input_shape[1],input_shape[2],filters))([q_norm,k_norm,v])

    conv = tf.keras.layers.Conv2D(filters,1,activation='relu')(dot_qkv)

    fast_attention = tf.keras.layers.Concatenate()([conv,inputs])

    return fast_attention

def upsample(filters, size, apply_dropout=False):
    initializer = tf.random_normal_initializer(0., 0.02)

    result = tf.keras.Sequential()
    result.add(
        tf.keras.layers.Conv2DTranspose(filters, size, strides=2,
                                    padding='same',
                                    kernel_initializer=initializer,
                                    use_bias=False))

    result.add(tf.keras.layers.BatchNormalization())

    if apply_dropout:
        result.add(tf.keras.layers.Dropout(0.5))

    result.add(tf.keras.layers.ReLU())

    return result

# Num filers y kernel de la convolución
up_stack = [
    upsample(512, 3),  # 4x4 -> 8x8
    upsample(256, 3),  # 8x8 -> 16x16
    upsample(128, 3),  # 16x16 -> 32x32
    upsample(64, 3),   # 32x32 -> 64x64
]

up_stack_ext = [
    upsample(512, 3),  # 4x4 -> 8x8
    upsample(256, 3),  # 8x8 -> 16x16
    upsample(128, 3),  # 16x16 -> 32x32
    upsample(64, 3),   # 32x32 -> 64x64
    upsample(64,3)
]

channels = [256,128,64]
def unet_model_attention(output_channels,input_shape,channels_attention):
    inputs = tf.keras.layers.Input(shape=[input_shape[0],input_shape[1],3])
    x = inputs

  # Downsampling through the model
    skips =down_stack(x)
    x=skips[-1]
    x = FastAttention(x,512,batch_size,x.shape,channels_attention)
    skips = reversed(skips[:-1])

# Upsampling and establishing the skip connections
    for up, skip,channel in zip(up_stack, skips,channels):
        x = up(x)
        fast_attention=FastAttention(skip,channel,batch_size,skip.shape,channels_attention)
        concat = tf.keras.layers.Concatenate()
        x = concat([x, fast_attention])

  # This is the last layer of the model
    last = tf.keras.layers.Conv2DTranspose(
        output_channels, 3, strides=2,
        padding='same')  #64x64 -> 128x128

    x = last(x)
    #x=tf.keras.layers.Softmax()(x)

    return tf.keras.Model(inputs=inputs, outputs=x)

def unet_model(output_channels,input_shape):
    inputs = tf.keras.layers.Input(shape=[input_shape[0],input_shape[1],3])
    x = inputs

  # Downsampling through the model
    skips = down_stack(x)
    x = skips[-1]
    skips = reversed(skips[:-1])

  # Upsampling and establishing the skip connections
    for up, skip in zip(up_stack, skips):
        x = up(x)
        concat = tf.keras.layers.Concatenate()
        x = concat([x, skip])

  # This is the last layer of the model
    last = tf.keras.layers.Conv2DTranspose(
        output_channels, 3, strides=2,
        padding='same')  #64x64 -> 128x128

    x = last(x)

  #x=tf.keras.layers.Softmax()(x)

    return tf.keras.Model(inputs=inputs, outputs=x)

def unet_model_ext(output_channels,input_shape,channels_attention):
    inputs = tf.keras.layers.Input(shape=[input_shape[0],input_shape[1],3])
    x = inputs

  # Downsampling through the model
    skips =down_stack(x)
    x=skips[-1]
    x = FastAttention(x,512,batch_size,x.shape,channels_attention)
    skips = reversed(skips[:-1])

# Upsampling and establishing the skip connections
    for up, skip,channel in zip(up_stack_ext, skips,channels):
        x = up(x)
        fast_attention=FastAttention(skip,channel,batch_size,skip.shape,channels_attention)
        concat = tf.keras.layers.Concatenate()
        x = concat([x, fast_attention])

    x=up_stack_ext[-1](x)

  # This is the last layer of the model
    last = tf.keras.layers.Conv2D(output_channels, 3,padding='same')

    x = last(x)
    #x=tf.keras.layers.Softmax()(x)

    return tf.keras.Model(inputs=inputs, outputs=x)

def load_model_u(ejecucion):
    OUTPUT_CHANNELS=9
    cats=['BG','bicycle','car','motorcycle','airplane','bus','train','truck','boat']
    if("unet/ejecucion1" in ejecucion or "ejecucion2" in ejecucion):
        model=unet_model(OUTPUT_CHANNELS,input_shape)
        #model.load_weights(ejecucion)
    elif("ejecucion5" in ejecucion):
        model = unet_model_attention(OUTPUT_CHANNELS,input_shape,128)
    elif("ejecucion4" in ejecucion):
        model = unet_model_attention(OUTPUT_CHANNELS,input_shape,64)
        #model.load_weights(ejecucion)#,custom_objects={'fast_attention_layer':fast_attention_layer})
    else:
        model = unet_model_ext(91,input_shape,64)
        cats=['BG','bicycle','car','motorcycle','airplane','bus','train','truck','boat',
            'traffic light','fire hydrant','street sign','stop sign','parking meter','bench','bird',
            'cat','dog','horse','sheep', 'cow','elephant','bear','zebra','giraffe','hat','backpack',
            'umbrella','shoe','eye glasses','handbag','tie','suitcase','frisbee','skis','snowboard',
            'sports ball','kite','baseball bat','baseball glove','skateboard','surfboard','tennis racket',
            'bottle','plate','wine glass','cup','fork','knife','spoon','bowl','banana','apple','sandwich',
            'orange','broccoli','carrot','hot dog','pizza','donut','cake','chair','couch','potted plant',
            'bed','mirror','dining table','window','desk','toilet','door','tv','laptop','mouse','remote',
            'keyboard','cell phone','microwave','oven','toaster','sink','refrigerator','blender','book',
            'clock','vase','scissors','teddy bear','hair drier','tooth brush', 'hair brush']
    model.load_weights(ejecucion)
    return model,preprocess_input,cats

def predict(model,img,area,cats):
    input_shape=(640,640)
    predictions=model.predict(img)
    mask,masks=create_mask(predictions,len(cats),input_shape)
    return categories(masks,predictions,area,cats)

def create_mask(m,lenClasses,input_shape):
    mask=np.zeros((int(input_shape[0]/2),int(input_shape[1]/2)))
    masks=np.zeros((int(input_shape[0]/2),int(input_shape[1]/2),lenClasses))
    for i in range((int(input_shape[0]/2))):
        for j in range((int(input_shape[1]/2))):
            categoria=0
            for k in range(lenClasses):
                if m[0,i,j,k] > m[0,i,j,categoria]:
                    mask[i,j]=k
                    masks[i,j,k]=k
                    masks[i,j,categoria]=0
                    categoria=k
            #print(categoria)

    #plt.imshow(mask)
    return mask,masks

def categories(masks,predictions,area,cats):
    categories_response=[]
    for i in range(1,len(cats)):
        #label_img=label(masks[:,:,i],connectivity=2)
        #cleaned =morphology.remove_small_objects(label_img,min_size=area)
        #regions=regionprops(cleaned)
        #for p in regions:
        #    bbox,score,pixels=bbox_score_pixels(masks,predictions,p,i)
        #    categories_response.append({str(cats[i]) : {
        #        "bbox" : #bbox,
        #        "score" : str(score),
        #        "pixels" : pixels.tolist()
        #        }})
        score,pixels=score_pixels(masks,predictions,i)
        if(score>0):
            categories_response.append({str(cats[i]) : {
                "bbox" : None,
                "score" : str(score),
                "pixels" : pixels
            }})

    return categories_response

def bbox_score_pixels(masks,predictions,region,cat):
    bbox=region.bbox
    Is,Js=np.where(masks[:,:,cat]==cat)
    scores=softmax(sum(predictions[0,Is,Js,:])/len(Is))
    #print("Softmax: ")
    #print(scores)
    #print(sum(predictions[0,region.coords[0],region.coords[1],:])/len(region.coords[0]))
    #print(cat)
    score=scores[cat]
    #print(score)
    #print(predictions[0,region.coords[0],region.coords[1],:])
    return (bbox[0],bbox[1],bbox[2]-bbox[0],bbox[3]-bbox[1]),score,region.coords

def score_pixels(masks,predictions,cat):
    Is,Js=np.where(masks[:,:,cat]==cat)
    if(len(Is)>0):
        scores=softmax(sum(predictions[0,Is,Js,:])/len(Is))
        score=scores[cat]
        return score, [Js.tolist(),Is.tolist()]
    else:
        return 0,[]
