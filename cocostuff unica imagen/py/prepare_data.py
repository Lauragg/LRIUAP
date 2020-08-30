from pycocotools.coco import COCO
import random
import cv2
import numpy as np
import skimage.io as io

def subDataset(classes,dataDir='./COCO2014',dataType='train'):
    annFile='{}/annotations/instances_{}2014.json'.format(dataDir,dataType)

    coco=COCO(annFile)
    images = []
    if classes!=None and classes!=[]:
        for clase in classes:
            subsetCatID=coco.getCatIds(catNms=clase)
            subsetImgID=coco.getImgIds(catIds=subsetCatID)
            images+=coco.loadImgs(subsetImgID)

    else:
        subsetImgID=coco.getImgIds()
        images=coco.loadImgs(subsetImgID)

    aux=[]
    for image in images:
        if image not in aux:
            aux.append(image)

    return aux, coco

def createSegmentationMask(image,classesID,input_shape,coco):
    mask = np.zeros((int(input_shape[0]/4),int(input_shape[1]/4)))
    annsID=coco.getAnnIds(imgIds=image['id'],catIds=classesID,iscrowd=None)
    cats=coco.loadCats(classesID) # Sin usar de momento
    instanceAnn=coco.loadAnns(annsID)
    for i in range(len(instanceAnn)):
        #if not isinstance(ann,int):
        pixel_value=instanceAnn[i]['category_id']
        #mask=np.maximun(coco.annToMask(ann[i])*pixel_value,mask)
        if pixel_value > 0:
            cocomask=cv2.resize(coco.annToMask(instanceAnn[i])*pixel_value,(int(input_shape[1]/4),int(input_shape[0]/4)))
            mask=np.maximum(cocomask,mask)

    mask=mask.reshape(int(input_shape[0]/4),int(input_shape[1]/4),1)/183.0

    return mask

def getImage(imageObj, img_folder, input_image_size):
    # Read and normalize an image
    #ruta=(img_folder + '/' + imageObj+'.jpg').rstrip("/n")
    #print(ruta)
    train_img = io.imread(img_folder + '/' + imageObj+'.jpg')/255.0
    # Resize
    #print(input_image_size)
    train_img = cv2.resize(train_img, (input_image_size[1],input_image_size[0]))
    if (len(train_img.shape)==3 and train_img.shape[2]==3): # If it is a RGB 3 channel image
        return train_img
    else: # To handle a black and white image, increase dimensions to 3
        stacked_img = np.stack((train_img,)*3, axis=-1)
        return stacked_img

def getMask(imageObj, img_folder, input_image_size):
    # Read and normalize an image
    name=imageObj.split("_")[-1]

    train_img = io.imread(img_folder + '/' + name+'.png')/183.0#255#183.0
    # Resize
    #print(input_image_size)
    train_img = cv2.resize(train_img, (int(input_image_size[1]/4),int(input_image_size[0]/4)))
    train_img=train_img.reshape(int(input_image_size[0]/4),int(input_image_size[1]/4),1)
    #if (len(train_img.shape)==3 and train_img.shape[2]==3): # If it is a RGB 3 channel image
    #    return train_img
    #else: # To handle a black and white image, increase dimensions to 3
    #    stacked_img = np.stack((train_img,)*3, axis=-1)
    #    return stacked_img
    return train_img


def dataGeneratorCoco(images,folder,input_shape=(1024,2048),mode='train',batch_size=16):
    img_folder='{}/images'.format(folder,mode)
    mask_folder='{}/gray'.format(folder)
    #catsId=coco.getCatIds(catNms=classes)

    imagesList='{}/imageLists/{}'.format(folder,images)

    file=open(imagesList,"r")
    #c=0
    while(True):
        img = np.zeros((batch_size, input_shape[0], input_shape[1], 3)).astype('float')
        m = np.zeros((batch_size, int(input_shape[0]/4), int(input_shape[1]/4), 1)).astype('float')

        #for i in range(c,c+batch_size):
        for i in range(0,batch_size):
        #for imageObj in images:
            #print(input_shape)
            imageObj=file.readline()
            if not imageObj:
                file.close()
                file=open(imagesList,"r")
                imageObj=file.readline()
            imageObj=imageObj[:-1]
            image=getImage(imageObj,img_folder,input_shape)

            #mask=createSegmentationMask(imageObj,catsId,input_shape,coco)
            mask=getMask(imageObj,mask_folder,input_shape)

            #print(image.shape)
            #print(img[0].shape)
            img[i]=image
            m[i]=mask

        #c+=batch_size
        #if(c + batch_size >= len(image)):

        #print(m[0])
        yield img,m
