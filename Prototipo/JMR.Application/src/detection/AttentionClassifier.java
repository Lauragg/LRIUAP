/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package detection;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jmr.descriptor.label.Classifier;
import jmr.descriptor.label.LabeledClassification;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author Laura Gómez Garrido (lauragogar@correo.ugr.es)
 */
public class AttentionClassifier implements Classifier<String, LabeledClassification> {
    protected TCPClient tcpClient;
    protected String model;
    protected int area;
    
    public AttentionClassifier(String m, int area){
        this.tcpClient = new TCPClient("localhost",5000);
        this.setModel(m);
        this.area=area;
    }

    @Override
    public RegionClassification apply(String t) {
        List<String> labels = new ArrayList();
        List<Double> weights = new ArrayList();
        List<List<Point2D>> polygons = new ArrayList();
        List<Rectangle> bboxs = new ArrayList();

        try {
            JSONObject object = new JSONObject();
            object.put("option","F");
            object.put("path_img_selected",t);
            object.put("area",this.area);
            
            String info = this.tcpClient.sendPetition(object.toJSONString() + "\n");
            JSONParser parser = new JSONParser();
            JSONArray response = (JSONArray) parser.parse(info);
            
            for(Object obj : response){
                JSONObject objJson = (JSONObject) obj;
                objJson.keySet().forEach(classNm -> {
                    String className= (String) classNm;
                    JSONObject infoClass= (JSONObject) objJson.get(className);
                    
                    Double weight = Double.parseDouble((String)infoClass.get("score"));
                    JSONArray pixels = (JSONArray) infoClass.get("pixels");
                    
                    JSONArray bbox=(JSONArray) infoClass.get("bbox");
                    if(bbox != null){
                        int x=((Long)bbox.get(1)).intValue();
                        int y=((Long)bbox.get(0)).intValue();
                        int w=((Long)bbox.get(3)).intValue();
                        int h=((Long)bbox.get(2)).intValue();
                        bboxs.add(new Rectangle(x,y,w,h));
                    }
                    
                    labels.add(className);
                    weights.add(weight);
                    polygons.add(jsonArrayToPolygon(pixels));
                    
                });
            }
        } catch (ParseException ex) {
            Logger.getLogger(AttentionClassifier.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return new RegionClassification(){
            @Override
            public List<List<Point2D>> getShapes() {
                return polygons;
            }

            @Override
            public List<String> getLabels() {
                return labels;
            }

            @Override
            public boolean isWeighted() {
                return true;
            }

            @Override
            public List<Double> getWeights() {
                return weights;
            }
            
            @Override
            public List<Rectangle> getBboxs(){
                return bboxs;
            }
            
        };

        
    }
    
    public void closeConnection(){
        this.tcpClient.closeConnection();
    }
    
    private List<Point2D> jsonArrayToPolygon(JSONArray array){
        List<Point2D> arr=new ArrayList();
        JSONArray jsonX=(JSONArray)array.get(0);
        JSONArray jsonY=(JSONArray)array.get(1);
        for(int i=0; i<jsonX.size();i++){
            //JSONArray jsonA=(JSONArray)array.get(i);
            //arr.add(new Point2D.Double(
            //        (Long)jsonA.get(1),
            //        (Long)jsonA.get(0)));
            arr.add(new Point2D.Double(
                    (Long)jsonX.get(i),
                    (Long)jsonY.get(i)
            ));
        }
        return arr;
    }
    
    public final void setModel(String m){
        //System.out.println(m);
        if(this.model == null || !this.model.equals(m)){
            this.model=m;
            //System.out.println(this.model);
            JSONObject object = new JSONObject();
            object.put("option","L");
            object.put("modelo",m);
            
            this.tcpClient.sendPetition(object.toJSONString() + "\n");
        }
    }
    
    public void setArea(int p){
        this.area=p;
    }
    
    public String getModel(){return this.model;}
    
    public int getArea(){return this.area;}
    
    //public TYPE getSegmentation(){} FIXMEla
    
}
