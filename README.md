# GalleryML
An Android app for cleaning near-duplicated images using machine learning algorithms

The app uses ImageNet convolutional neural network with TensorFlow Lite (TensorFlow's lightweight solution for mobile and embedded devices) for image classification, then takes the output feature vectors and uses them as an input points to the DBScan algorithm for clustering.

#### DBScan simulation on 2-dimensional space

![DBScan simulation](https://media.giphy.com/media/OVJBPIB6oL3a0/giphy.gif)
