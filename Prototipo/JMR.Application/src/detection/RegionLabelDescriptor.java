/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package detection;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import jmr.descriptor.Comparator;
import jmr.descriptor.label.Classifier;
import jmr.descriptor.label.LabelDescriptor;

/**
 *
 * @author laura
 */
public class RegionLabelDescriptor extends LabelDescriptor<String>{
    private List<List<Point2D>> shapes;
    private List<Rectangle> bboxs;
    
    private static Comparator REGION_DEFAULT_COMPARATOR=new IoUComparator();
    public RegionLabelDescriptor(String media, Classifier classifier) {
        super(media, classifier);         
    }   
    public RegionLabelDescriptor(String media) {
        this(media, LabelDescriptor.getDefaultClassifier());
    }
    
    public RegionLabelDescriptor(String first, String[] queryLabel) {
        super(first, queryLabel);
    }
    
    public List<Point2D> getShape(int index){
        return this.shapes.get(index);
    }
    public List<List<Point2D>> getShapes(){
        return this.shapes;
    }
    
    public List<Rectangle> getBboxs(){
        return this.bboxs;
    }
    
    public void setShapes(List<List<Point2D>> shapes){
        this.shapes=shapes;
    }
    
    public static void setRegionDefaultComparator(Comparator comparator){
        REGION_DEFAULT_COMPARATOR=comparator;
    }
    
    
    @Override
    public void init(String media){
        super.init(media);
        if(media!=null && this.getClassifier()!= null && !this.getClassifier().getClass().getSimpleName().equals("DefaultClassifier")){
            RegionClassification classification = (RegionClassification)(super.getClassifier().apply(media));
            this.shapes = classification.getShapes();
            this.bboxs = classification.getBboxs();
            this.setComparator(REGION_DEFAULT_COMPARATOR);
        }
    }
    
    static public class IoUComparator implements Comparator<RegionLabelDescriptor,Double>{
        
        /**
         * Type of distance aggregation based on the maximum.
         */
        static public final int TYPE_MAX = 1;
        /**
         * Type of distance aggregation based on the minimum.
         */
        static public final int TYPE_MIN = 2;
        /**
         * Type of distance aggregation based on the mean.
         */
        static public final int TYPE_MEAN = 3;
        /**
         * Type of distance aggregation based on the euclidean distance.
         */
        static public final int TYPE_EUCLIDEAN = 4;
        /**
         * Type of aggregation used in this comparator
         */
        private int type; 
        /**
         * Comparator used for the comparation between labels
         */
        static protected Comparator<LabelDescriptor,Double> DEFAULT_COMPARATOR = null;
        
        /**
         * The unary operator used to initialize the distance accumulator (as a
         * function of the fisrt distance).
         */
        private transient UnaryOperator<Double> op_init;  // Class non serializable 
        /**
         * The binary operator used to aggregate a new distance to the previous
         * ones.
         */
        private transient BinaryOperator<Double> op_aggregation; // Class non serializable
        /**
         * Constructs a new comparator that compare labels based on Default Comparator
         * and pixels based on IoU and the given type of distance aggreagation.
         * @param type the type of distance aggregation
         */
        public IoUComparator(int type){
            setType(type);
            if(IoUComparator.DEFAULT_COMPARATOR==null){
                IoUComparator.DEFAULT_COMPARATOR=new LabelDescriptor.EqualComparator();
            }
        }
        /**
         * Default Constructor with TYPE_MEAN.
         */
        public IoUComparator(){
            this(IoUComparator.TYPE_MEAN);
        }
        
        public void setType(int type){
            switch (type) {
                case TYPE_MAX:
                    op_init = (a) -> a;
                    op_aggregation = (a, b) -> Math.max(a, b);
                    break;
                case TYPE_MIN:
                    op_init = (a) -> a;
                    op_aggregation = (a, b) -> Math.min(a, b);
                    break;
                case TYPE_MEAN:
                    op_init = (a) -> a;
                    op_aggregation = (a, b) -> (a + b);
                    break;
                case TYPE_EUCLIDEAN:
                    op_init = (a) -> (a * a);
                    op_aggregation = (a, b) -> (a + b * b);
                    break;
                default:
                    throw new InvalidParameterException("Invalid distance aggregator type");
            }
            
            this.type=type;
        }
        /**
         * Set de default LabelDescriptor Comparator. Implementes Equal and Include, 
         * SoftEqual and WeightBased will return Double.POSITIVE_INFINITY on apply method.
         * @param comparator 
         */
        public static void setDefaultComparator(Comparator comparator){
            IoUComparator.DEFAULT_COMPARATOR=comparator;
        }


        @Override
        public Double apply(RegionLabelDescriptor t, RegionLabelDescriptor u) {
            Double labelComparation=IoUComparator.DEFAULT_COMPARATOR.apply(t, u);
            if(labelComparation==Double.POSITIVE_INFINITY)
                return Double.POSITIVE_INFINITY;
            

            /*
            * Asumimos que el orden es siempre el mismo, lo que puedo cambiar es 
            * la multiplicidad de los objetos o si u tiene objetos que t no.
            */
            if(IoUComparator.DEFAULT_COMPARATOR.getClass().getSimpleName().equals(LabelDescriptor.EqualComparator.class.getSimpleName()) ||
                    IoUComparator.DEFAULT_COMPARATOR.getClass().getSimpleName().equals(LabelDescriptor.InclusionComparator.class.getSimpleName())){
                
                String label=t.getLabel(0);
                int t_index=0;
                int u_index=0;
                ArrayList<Double> iou=new ArrayList();
                while(t_index<t.size()){
                    Set<Point2D> t_list=new HashSet();
                    Set<Point2D> u_list=new HashSet();
                    while(!u.getLabel(u_index).equals(label))
                        u_index++;
                    while(u_index < u.size() && u.getLabel(u_index).equals(label)){
                        u_list.addAll((ArrayList)u.getShape(u_index));
                        u_index++;
                    }
                    while(t_index < t.size() && t.getLabel(t_index).equals(label)){
                        t_list.addAll((ArrayList)t.getShape(t_index));
                        t_index++;
                    }
                    if(t_index < t.size())
                        label=t.getLabel(t_index);
                    
                    Set<Point2D> inter=new HashSet(t_list);
                    inter.retainAll(u_list); // Interseccion de t y u;
                    
                    t_list.addAll(u_list); // Unión de t y u
                    /*
                    * IoU = intersection/union    IoU: NxN -> (0,1]
                    * 0 distintos - 1 iguales
                    * 1/IoU = union/intersection  1/IoU NxN -> [1,+infinity)
                    * + infinity distintos - 1 iguales
                    * 1/IoU -1                    1/IoU -1 NxN ->[0,+infinity) 
                    * + infinity distintos - 0 iguales
                    */
                    iou.add(Double.valueOf(t_list.size())/Double.valueOf(inter.size())-1);
                }
                
                Double dist = this.op_init.apply(iou.get(0));
                for(int i=1; i<iou.size();i++)
                    dist=this.op_aggregation.apply(dist,iou.get(i));
                switch (type) {
                    case TYPE_MEAN:
                        return dist/iou.size();
                    case TYPE_EUCLIDEAN:
                        return Math.sqrt(dist);
                    default:
                        return dist;
                }
                
                
            }else{
                return Double.POSITIVE_INFINITY;
            }

        }
        
    }

}
