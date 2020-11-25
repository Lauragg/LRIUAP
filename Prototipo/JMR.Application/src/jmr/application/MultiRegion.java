/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmr.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author laura
 */
public class MultiRegion {
    private BufferedImage source;
    private List<List<Point2D>> shapes = null;
    private List<Rectangle> bbox = null;
    private List<String> labels = null;
    private List<Double> weights = null;
    
    public MultiRegion(BufferedImage image){
        this.source=image;
        if(image!=null){
            shapes = new ArrayList();
            bbox = new ArrayList();
            bbox.add(new Rectangle(image.getWidth(),image.getHeight()));
        }
    }
    
    public MultiRegion(BufferedImage image,List<List<Point2D>> s,List<String> l,List<Double> p, List<Rectangle> b){
        this.source=image;
        if(image!=null){
            shapes = s;
            labels = l;
            weights = p;
            bbox = b;
        }
    }
    
    public BufferedImage getSource(){
        return source;
    }
    
    public List<List<Point2D>> getShapes(){
        return shapes;
    }
    
    public List<Point2D> getShape(int i){
        return this.shapes.get(i);
    }
    
    public boolean contains(int i,Point2D p){
        return shapes.get(i).contains(p);
    }
    
    public boolean contains(int i,int x, int y){
       return shapes.get(i).contains(new Point2D.Double(x,y));
    }
    
    public List<Point2D> whereIsContained(Point2D p){
        for(List<Point2D> shape : shapes){
            if(shape.contains(p))
                return shape;
        }
        return null;
    }
    
    public List<Point2D>  whereIsContained(int x, int y){
        for(int i=0; i<shapes.size();i++){
            if(this.contains(i,x,y))
                return shapes.get(i);
        }
        return null;
    }
    
    public Color getRGB(Point2D p){
        if(this.whereIsContained(p)!=null){
            return new Color(source.getRGB((int)p.getX(), (int)p.getY()));
        }
        return null;
    }
    
    public Color getRGB(int x, int y){
        if(this.whereIsContained(x,y)!=null){
            return new Color(source.getRGB(x,y));
        }
        return null;
    }
    
    public BufferedImage createImage(){
        BufferedImage output = new BufferedImage(
                    320,
                    320,
                    BufferedImage.TYPE_INT_ARGB
        );
        
        Random rand = new Random();
        Graphics2D g2d=(Graphics2D)output.createGraphics();
        g2d.drawImage(this.source.getScaledInstance(320, 320, 0),null,null);
        for(List<Point2D> shape : shapes){
            g2d.setColor(new Color(rand.nextFloat(),rand.nextFloat(),rand.nextFloat()));
            for(Point2D p : shape){
                g2d.draw(new Line2D.Double(p,p));
            }
        }
        return output;
    }
    
    public BufferedImage createImage(int i){
        BufferedImage output = new BufferedImage(
                    320,
                    320,
                    BufferedImage.TYPE_INT_ARGB
        );
        
        Random rand = new Random();
        Graphics2D g2d=(Graphics2D)output.createGraphics();
        g2d.drawImage(this.source.getScaledInstance(320, 320, 0),null,null);
        g2d.setColor(new Color(rand.nextFloat(),rand.nextFloat(),rand.nextFloat()));
        for(Point2D p : shapes.get(i)){
                g2d.draw(new Line2D.Double(p,p));
        }
        return output;
    }
    
    public BufferedImage createImage(Shape shape){
        BufferedImage output = new BufferedImage(
                    320,
                    320,
                    BufferedImage.TYPE_INT_ARGB
        );
        
        Random rand = new Random();
        Graphics2D g2d=(Graphics2D)output.createGraphics();
        g2d.drawImage(this.source.getScaledInstance(320, 320, 0),null,null);
        g2d.setColor(new Color(rand.nextFloat(),rand.nextFloat(),rand.nextFloat()));
        g2d.draw(shape);
        return output;
    }
    
    public BufferedImage createImage(boolean pixels,boolean bbox,boolean label){
        BufferedImage output = new BufferedImage(
                    320,
                    320,
                    BufferedImage.TYPE_INT_ARGB
        );
        
        Random rand = new Random();
        Graphics2D g2d=(Graphics2D)output.createGraphics();
        g2d.drawImage(this.source.getScaledInstance(320, 320, 0),null,null);
        for(int i=0; i<shapes.size();i++){
            g2d.setColor(new Color(rand.nextFloat(),rand.nextFloat(),rand.nextFloat()));
            if(pixels)
                for(Point2D p : shapes.get(i)){
                    g2d.draw(new Line2D.Double(p,p));
                }
            if(bbox && !this.bbox.isEmpty())
                g2d.draw(this.bbox.get(i));
            if(label && this.labels != null){
                if(this.weights != null){
                    g2d.drawString(
                            this.labels.get(i)+" : "+this.weights.get(i),
                            (int)this.shapes.get(i).get(0).getX(),
                            (int)this.shapes.get(i).get(0).getY()-10
                    );
                }else{
                    g2d.drawString(
                            this.labels.get(i),
                            (int)this.shapes.get(i).get(0).getX(),
                            (int)this.shapes.get(i).get(0).getY()-10
                    );
                }
            }
        }
        return output;
    }
}
