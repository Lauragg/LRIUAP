from pycocotools.coco import COCO
import random
import cv2
import numpy as np
import skimage.io as io

def subDataset(classes,dataDir='./COCO2017',dataType='train'):
    annFile='{}/annotations/instances_{}2017.json'.format(dataDir,dataType)

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

def createSegmentationMask(image,classesID,input_shape,coco,lenClasses):
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

    mask=mask.reshape(int(input_shape[0]/4),int(input_shape[1]/4),1)

    return mask

def getImage(imageObj, img_folder, input_image_size):
    # Read and normalize an image
    train_img = io.imread(img_folder + '/' + imageObj['file_name'])/255.0
    # Resize
    #print(input_image_size)
    train_img = cv2.resize(train_img, (input_image_size[1],input_image_size[0]))
    if (len(train_img.shape)==3 and train_img.shape[2]==3): # If it is a RGB 3 channel image
        return train_img
    else: # To handle a black and white image, increase dimensions to 3
        stacked_img = np.stack((train_img,)*3, axis=-1)
        return stacked_img


def dataGeneratorCoco(images,classes,coco,folder,input_shape=(1024,2048),mode='train',batch_size=16):
    img_folder='{}/{}2017'.format(folder,mode)
    catsId=coco.getCatIds(catNms=classes)

    c=0
    while(True):
        img = np.zeros((batch_size, input_shape[0], input_shape[1], 3)).astype('float')
        m = np.zeros((batch_size, int(input_shape[0]/4), int(input_shape[1]/4), 1)).astype('float')

        for i in range(c,c+batch_size):
        #for imageObj in images:
            #print(input_shape)
            imageObj=images[i]
            image=getImage(imageObj,img_folder,input_shape)

            mask=createSegmentationMask(imageObj,catsId,input_shape,coco,len(classes))

            #print(image.shape)
            #print(img[0].shape)
            img[i-c]=image
            m[i-c]=mask

        c+=batch_size
        if(c + batch_size >= len(image)):
            c=0
            random.shuffle(images)

        yield img,m
