This project has been forked from the core of Processing 3.5.3 release. 
https://github.com/processing/processing/releases/tag/processing-0269-3.5.3

This fork aims at keeping only the javafx part of the work (and use it as sole render), allowing to manage it as a classical javafx Node which can easily be embeded in a javafx app. 

We also use maven for building, contrary to the official repo. It also has been updated to work with java 17.

After the creation of your PApplet, you should just have to call 

    ((Node) this.myApplet.getSurface().getNative())

to get a usable, resizable javafx node.

We have tried to respect licences stuff (we keep LGPL licences). 
If you see an anomaly, please tell us.
