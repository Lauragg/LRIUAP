#!/usr/bin/env python
# coding: utf-8

# In[ ]:


#import os
#os.environ["CUDA_DEVICE_ORDER"]="PCI_BUS_ID";

# The GPU id to use, usually either "0" or "1";
#os.environ["CUDA_VISIBLE_DEVICES"]="0";

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers


# In[ ]:
class fast_attention_layer(tf.keras.layers.Layer):
    """docstring for multiply."""

    def __init__(self,batch_size,input_shapeQK,input_shapeV):
        super(fast_attention_layer, self).__init__()
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




# In[ ]:


def FastAttention(inputs,filters,batch_size,input_shape):
    #inputs=keras.Input(shape=input_shape)

    q=layers.Conv2D(filters,1)(inputs) # Aquí vale 32 según el paper
    q_norm = layers.BatchNormalization()(q)

    k=layers.Conv2D(filters,1)(inputs)
    k_norm = layers.BatchNormalization()(k) # Aquí vale 32 según el paper

    v=layers.Conv2D(filters,1,activation='relu')(inputs)

    #dot_kv=layers.multiply([k_norm,v])
    #dot_qkv=layers.multiply([q_norm,dot_kv])
    dot_qkv=fast_attention_layer(batch_size,(input_shape[1],input_shape[2],filters),(input_shape[1],input_shape[2],filters))([q_norm,k_norm,v])

    conv = layers.Conv2D(filters,1,activation='relu')(dot_qkv)

    fast_attention = layers.add([conv,inputs])

    return fast_attention


# In[ ]:


def FuseUp(up,fuse,filters):
    upp=layers.UpSampling2D(size=(2,2))(up)
    uprelu=layers.ReLU()(upp)
    add=layers.add([uprelu,fuse])
    convbn=layers.Conv2D(filters,1)(add)

    return convbn


# In[ ]:


def generate_model(input_shape,categories,backbone,layer,batch_size):
    resdown1=backbone.get_layer(layer[0]).output
    resdown2=backbone.get_layer(layer[1]).output
    resdown3=backbone.get_layer(layer[2]).output
    resdown4=backbone.output

    fast_attention1=FastAttention(resdown1,64,batch_size,resdown1.shape)
    fast_attention2=FastAttention(resdown2,128,batch_size,resdown2.shape)
    fast_attention3=FastAttention(resdown3,256,batch_size,resdown3.shape)
    fast_attention4=FastAttention(resdown4,512,batch_size,resdown4.shape)

    fuseup4=layers.Conv2D(256,1)(fast_attention4)
    fuseup3=FuseUp(fuseup4,fast_attention3,128)
    fuseup2=FuseUp(fuseup3,fast_attention2,64)
    fuseup1=FuseUp(fuseup2,fast_attention1,1)

    model=keras.models.Model(inputs=[backbone.input],outputs=[fuseup1])

    return model
