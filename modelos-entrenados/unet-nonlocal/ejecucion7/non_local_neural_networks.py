################################################################################
#
# Para evitar usar otras GPUS
#
################################################################################
import os
os.environ["CUDA_DEVICE_ORDER"]="PCI_BUS_ID";

# The GPU id to use, usually either "0" or "1";
os.environ["CUDA_VISIBLE_DEVICES"]="4";


################################################################################
#
# Vamos a seguir paso a paso el tutorial de la página de tensorflow sobre U-net
# que lo hace utilizando como backbone otra arquitectura. Sólo que usaremos la
# Resnet en nuestro caso. También vamos a añadir la lectura buena de imágenes
# (keras.image_load en lugar de io.im_read).
# Primero lo vamos a crear sin los módulos de atención. Al rato ya se los añadimos.
#
################################################################################
import os
import random
import numpy as np
import matplotlib.pyplot as plt
from tqdm import tqdm_notebook, tnrange
from sklearn.model_selection import train_test_split
import imgaug.augmenters as iaa

import tensorflow as tf
from tensorflow import keras
from keras.preprocessing import image
from keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from classification_models.tfkeras import Classifiers
#from tensorflow_examples.models.pix2pix import pix2pix

#import tensorflow_datasets as tfds
#tfds.disable_progress_bar()

#from IPython.display import clear_output


################################################################################
#
# Variabales
#
################################################################################
OUTPUT_CHANNELS=91
input_shape=(640,640)
optimizer='adam'
#loss="binary_crossentropy"#
loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True)
class UpdatedMeanIoU(tf.keras.metrics.MeanIoU):
  def __init__(self,
               y_true=None,
               y_pred=None,
               num_classes=None,
               name=None,
               dtype=None):
    super(UpdatedMeanIoU, self).__init__(num_classes = num_classes,name=name, dtype=dtype)

  def update_state(self, y_true, y_pred, sample_weight=None):
    y_pred = tf.math.argmax(y_pred, axis=-1)
    return super().update_state(y_true, y_pred, sample_weight)

metrics=['accuracy',UpdatedMeanIoU(num_classes=OUTPUT_CHANNELS)]
callbacks = [
    EarlyStopping(patience=10, verbose=1),
    ReduceLROnPlateau(factor=0.1, patience=3, min_lr=0.0000000001, verbose=1),
    ModelCheckpoint('model-con-preprocess.h5', verbose=1, save_best_only=True, save_weights_only=False)
]

path_train = './COCO2017'
batch_size = 16
epochs=100
#path_test = './k-folds/primero/test/'
################################################################################
#
# Definir el modelo base (backbone) o codificador
#
################################################################################

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

################################################################################
#
# Definir la capa de atención
#
################################################################################
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

    def get_config(self):
        config=super().get_config().copy()
        config.update({
                'batch_size':self.batch_size,
                'input_shapeQK':self.input_shapeQK,
                'input_shapeV':self.input_shapeV,
                'trainable':self.trainable
        })
        return config

def FastAttention(inputs,filters,batch_size,input_shape):
    #inputs=keras.Input(shape=input_shape)
    channels=64
    q=tf.keras.layers.Conv2D(channels,1)(inputs) # Aquí vale 32 según el paper
    q_norm = tf.keras.layers.LayerNormalization()(q)

    k=tf.keras.layers.Conv2D(channels,1)(inputs)
    k_norm = tf.keras.layers.LayerNormalization()(k) # Aquí vale 32 según el paper

    v=tf.keras.layers.Conv2D(filters,1,activation='relu')(inputs)

    #dot_kv=layers.multiply([k_norm,v])
    #dot_qkv=layers.multiply([q_norm,dot_kv])
    dot_qkv=fast_attention_layer(batch_size,(input_shape[1],input_shape[2],channels),(input_shape[1],input_shape[2],filters))([q_norm,k_norm,v])

    conv = tf.keras.layers.Conv2D(filters,1,activation='relu')(dot_qkv)

    fast_attention = tf.keras.layers.Concatenate()([conv,inputs])

    return fast_attention



################################################################################
#
# Definir el decodificador. Como upsample se utiliza el definido en
#        https://www.tensorflow.org/tutorials/generative/pix2pix
# como indica la guía.
#
# Diferencia 2: Como decía el paper, usábamos directamente los bloques de UpSampling2D
# porque así nos ahorrábamos itempo de entrenamiento.
#
################################################################################

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

channels = [256,128,64]

def unet_model(output_channels,input_shape):
    inputs = tf.keras.layers.Input(shape=[input_shape[0],input_shape[1],3])
    x = inputs

  # Downsampling through the model
    skips =down_stack(x)
    x=skips[-1]
    x = FastAttention(x,512,batch_size,x.shape)
    skips = reversed(skips[:-1])

# Upsampling and establishing the skip connections
    for up, skip,channel in zip(up_stack, skips,channels):
        x = up(x)
        fast_attention=FastAttention(skip,channel,batch_size,skip.shape)
        concat = tf.keras.layers.Concatenate()
        x = concat([x, fast_attention])

  # This is the last layer of the model
    last = tf.keras.layers.Conv2DTranspose(
        output_channels, 3, strides=2,
        padding='same')  #64x64 -> 128x128

    x = last(x)
    #x=tf.keras.layers.ReLU()(x)
    #last = tf.keras.layers.Conv2DTranspose(
    #    output_channels, 3, strides=2,
    #    padding='same')
    #x=last(x)
    return tf.keras.Model(inputs=inputs, outputs=x)


################################################################################
#
# Creamos y compilamos el modelo
#
################################################################################
model = unet_model(OUTPUT_CHANNELS,input_shape)
model.compile(optimizer=optimizer,
              loss=loss,
              metrics=metrics)

################################################################################
#
# Cargamos los datos.  En lugar de usar un generador como en las versiones anteriores,
# cargaremos directamente la carpeta donde estan como en la api esa y como hacen en:
#    https://www.depends-on-the-definition.com/unet-keras-segmenting-images/
#
################################################################################

# Get and resize train images and masks
def get_data(path,batch_size, train=True,input_shape=(128,128)):
    if train:
        ids = next(os.walk(path + "/train"))[2]
    else:
        ids = next(os.walk(path + "/test"))[2]
    #pºrint('Getting and resizing images ... ')
    c=0
    augmentations=iaa.SomeOf((0,2),[
                iaa.Fliplr(0.5),
                iaa.Affine(scale=(0.5,1.5)),
                iaa.Affine(translate_percent=(-0.2,0.2))
                ],random_order=False)
    while(True):
        X = np.zeros((batch_size, input_shape[0], input_shape[1], 3), dtype=np.float32)
        y = np.zeros((batch_size, int(input_shape[0]/2),int(input_shape[1]/2), 1), dtype=np.int32)
        #for n, id_ in tqdm_notebook(enumerate(ids), total=len(ids)):
        for n in range(c,c+batch_size):
            id_=ids[n]
            if train:
            # Load images
                img = image.load_img(path + '/train/' + id_, target_size=input_shape)
                x_img = image.img_to_array(img)
                #x_img = resize(x_img, (128, 128, 1), mode='constant', preserve_range=True)
            else:
                img = image.load_img(path + '/test/' + id_, target_size=input_shape)
                x_img = image.img_to_array(img)

            # Load masks
            if train:
                id_=id_[:-3]+'png'
                mask = image.img_to_array(image.load_img(path + '/train-ann/' + id_,color_mode='grayscale', target_size=(int(input_shape[0]/2),int(input_shape[1]/2))),dtype='int32')
                #mask = resize(mask, (128, 128, 1), mode='constant', preserve_range=True)
            else:
                id_=id_[:-3]+'png'
                mask = image.img_to_array(image.load_img(path + '/test-ann/' + id_,color_mode='grayscale', target_size=(int(input_shape[0]/2),int(input_shape[1]/2))),dtype='int32')
                #mask = resize(mask, (128, 128, 1), mode='constant', preserve_range=True)

            # Save images
            if train:
                X[n-c],y[n-c] = augmentations(image=preprocess_input(x_img),segmentation_maps=np.expand_dims(mask,0))#x_img.squeeze() / 255
            else:
                X[n-c]=preprocess_input(x_img)
                y[n-c]=mask

        c+=batch_size
        if c+batch_size >= len(ids):
            c=0


        yield X,y

train_gen = get_data(path_train,batch_size,train=True,input_shape=input_shape)
val_gen = get_data(path_train,batch_size,train=False,input_shape=input_shape)

history = model.fit(x=train_gen,batch_size=batch_size,steps_per_epoch=117266/batch_size,validation_steps=4952/batch_size, epochs=epochs, callbacks=callbacks,
                    validation_data=val_gen)

################################################################################
#
# Guardamos el history y el modelo e imprimimos
#
################################################################################

model.save('./modelos/attention_buena.h5')

import pandas as pd


# convert the history.history dict to a pandas DataFrame:
hist_df = pd.DataFrame(history.history)

# save to json:
hist_json_file = 'history.json'
with open(hist_json_file, mode='w') as f:
    hist_df.to_json(f)

# or save to csv:
hist_csv_file = 'history.csv'
with open(hist_csv_file, mode='w') as f:
    hist_df.to_csv(f)


loss = history.history['loss']
val_loss = history.history['val_loss']
acc = history.history['accuracy']
val_acc = history.history['val_accuracy']

plt.figure()
plt.plot(loss, 'r', label='Training loss')
plt.plot(val_loss, 'b', label='Validation loss')
plt.title('Training and Validation Loss')
plt.xlabel('Epoch')
plt.ylabel('Loss Value')
plt.legend()
plt.show()
plt.savefig("loss.jpg", bbox_inches='tight')

plt.figure()
plt.plot(accuracy, 'r', label='Training accuracy')
plt.plot(val_accuracy, 'b', label='Validation accuracy')
plt.title('Training and Validation accuracy')
plt.xlabel('Epoch')
plt.ylabel('Accuracy Value')
plt.legend()
plt.show()
plt.savefig("accuracy.jpg", bbox_inches='tight')
