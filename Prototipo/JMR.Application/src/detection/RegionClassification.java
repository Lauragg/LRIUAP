/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package detection;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;
import jmr.descriptor.label.LabeledClassification;

/**
 *
 * @author laura
 */
public interface RegionClassification extends LabeledClassification{
    
    public List<List<Point2D>> getShapes();
    
    public List<Rectangle> getBboxs();
}
