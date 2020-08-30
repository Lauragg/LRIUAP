#!/usr/bin/env python
# coding: utf-8

# In[1]:

#import os
#os.environ["CUDA_DEVICE_ORDER"]="PCI_BUS_ID";

# The GPU id to use, usually either "0" or "1";
#os.environ["CUDA_VISIBLE_DEVICES"]="0";

import py.model_fastnet
import py.prepare_data


# In[2]:


#vector=np.zeros(20)
#for i in range(20):
#    vector[i]=pow((1-(i+1)/20),0.9)
#print(vector)
#!pip3 install tensorflow-addons


# In[3]:


import tensorflow as tf
from tensorflow import keras
import numpy as np
import tensorflow_addons as tfa


# In[4]:


LR_SCHEDULE = [
# (epoch a comenzar, learning rate) tupla
(1, 0.95488538), (2, 0.90953258), (3, 0.86392697), (4,0.81805215),(5,0.77188951),(6,0.72541785),
    (7,0.67861276),(8,0.63144587),(9,0.58388379),(10,0.53588673),(11,0.48740644),(12,0.43838329),
(13,0.3887418),(14,0.33838346),(15,0.28717459),(16,0.23492379),(17,0.18133521),(18,0.12589254),(19,0.06746414)]

def lr_schedule(epoch, lr):
    """Funcion de ayuda para recuperar el learning rate programado basado en la epoch."""
    if epoch < LR_SCHEDULE[0][0] or epoch > LR_SCHEDULE[-1][0]:
        return lr
    for i in range(len(LR_SCHEDULE)):
        if epoch == LR_SCHEDULE[i][0]:
            return LR_SCHEDULE[i][1]
    return lr

class LearningRateScheduler(tf.keras.callbacks.Callback):
    """Planificador de Learning rate que define el learning rate deacuerdo a lo programado.


  Arguments:
      schedule: una funcion que toma el indice del epoch
          (entero, indexado desde 0) y el learning rate actual
          como entradas y regresa un nuevo learning rate como salida (float).
    """

    def __init__(self, schedule):
        super(LearningRateScheduler, self).__init__()
        self.schedule = schedule

    def on_epoch_end(self, epoch, logs=None):
        if not hasattr(self.model.optimizer, 'lr'):
            raise ValueError('Optimizer must have a "lr" attribute.')
    # Obtener el learning rate actua del optimizer del modelo.
        lr = float(tf.keras.backend.get_value(self.model.optimizer.lr))
    # Llamar la funcion schedule para obtener el learning rate programado.
        scheduled_lr = self.schedule(epoch+1, lr)*lr
    # Definir el valor en el optimized antes de que la epoch comience
        tf.keras.backend.set_value(self.model.optimizer.lr, scheduled_lr)
        print('\nEpoch %05d: Learning rate is %6.4f.' % (epoch, scheduled_lr))


# In[5]:


checkpoint_filepath = './modelos'
model_checkpoint_callback = tf.keras.callbacks.ModelCheckpoint(
    filepath=checkpoint_filepath,
    save_weights_only=False,
    monitor='val_accuracy',
    mode='max',
    save_best_only=True)


# In[6]:


#categories=['person','bicycle','car','motorcycle','airplane','bus','train','truck','boat','traffic light','fire hydrant','stop sign','parking meter','bench']
#categories=['bicycle','car']#,'motorcycle','airplane','bus','train','truck','boat']
input_shape=(480,640)
batch_size=4
momentum=0.9
weight_decay=0.0005
learning_rate=0.01


# In[7]:


#optimizer=tf.keras.optimizers.Adam(lr=learning_rate,clipnorm=1.)
optimizer=tfa.optimizers.SGDW(lr=learning_rate,momentum=momentum,weight_decay=weight_decay)
#loss=tf.keras.losses.CategoricalCrossentropy(from_logits=True)
#loss='mse' - explota la loss
#loss='categorical_crossentropy' # loss baja y accuracy alto desde el inicio que va decreciendo (primera epochs)
loss='binary_crossentropy'
#loss = tf.keras.losses.CosineSimilarity() - loss negativa
#loss='msle' - loss pequeña y accuracy alta desde el inicio
learning_rate_callback=LearningRateScheduler(lr_schedule)
callbacks=[model_checkpoint_callback,learning_rate_callback]


# In[8]:


from classification_models.tfkeras import Classifiers

ResNet18, preprocess_imput = Classifiers.get('resnet18')
model_resnet18=ResNet18((input_shape[0],input_shape[1],3),weights='imagenet',include_top=False)
model_resnet18.trainable=False


# In[9]:


layers=["stage2_unit1_relu1","stage3_unit1_relu1","stage4_unit1_relu1"]
model=py.model_fastnet.generate_model(input_shape,model_resnet18,layers,batch_size)

model.summary()


# In[10]:


#train,coco_train=py.prepare_data.subDataset(categories)
#val,coco_val=py.prepare_data.subDataset(categories,dataType='val')


# In[11]:


train_gen=py.prepare_data.dataGeneratorCoco('train.txt','./COCOSTUFF',input_shape=input_shape,batch_size=batch_size)
val_gen=py.prepare_data.dataGeneratorCoco('test.txt','./COCOSTUFF',input_shape=input_shape,mode='val',batch_size=batch_size)


# In[ ]:





# In[ ]:


model.compile(optimizer=optimizer,
              loss=loss,
              metrics=['accuracy'])

history=model.fit(x=train_gen,
                  steps_per_epoch=9000/batch_size,
                  validation_steps=1000/batch_size,
                 validation_data=val_gen,
                 epochs=20,callbacks=callbacks)


# In[ ]:


model.save('./modelos/attention_buena.h5')

import pandas as pd

# assuming you stored your model.fit results in a 'history' variable:
history = model.fit(x_train, y_train, epochs=10)

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
