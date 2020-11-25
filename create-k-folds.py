import py.prepare_data

train_images='./k-folds/primero/train/'
test_images='./k-folds/primero/test/'
original='./COCOSTUFF/images/COCO_train2014_'
train_annotations='./k-folds/primero/train-ann/'
test_annotations='./k-folds/primero/test-ann/'
original_annotations='./COCOSTUFF/gray/'
archivo='./COCOSTUFF/imageLists/renamed.txt'
longitud=10000
sublongitud=1000

#py.prepare_data.create_k_fold_ann(archivo,longitud,sublongitud,
#    original,train_images,test_images,
#    original_annotations,train_annotations,test_annotations
#    )

path='./COCO2017'
py.prepare_data.create_k_fold_dir_cat(path)
