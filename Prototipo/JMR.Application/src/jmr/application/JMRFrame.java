package jmr.application;

import detection.AttentionClassifier;
import ui.AddGroupDialog;
import ui.ImageListInternalFrame;
import ui.LabelSetPanel;
import ui.ColorSetPanel;
import ui.JMRImageInternalFrame;
import detection.RegionLabelDescriptor;
import jfi.events.PixelEvent;
import jfi.events.PixelListener;
import jfi.iu.ImageInternalFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import jmr.db.ListDB;
import jmr.descriptor.DescriptorList;
import jmr.descriptor.GriddedDescriptor;
import jmr.descriptor.MediaDescriptor;
import jmr.descriptor.color.SingleColorDescriptor;
import jmr.initial.descriptor.mpeg7.MPEG7DominantColors;
import jmr.descriptor.color.MPEG7ColorStructure;
import jmr.descriptor.color.MPEG7ScalableColor;
import jmr.descriptor.label.LabelDescriptor;
import jmr.media.JMRExtendedBufferedImage;
import jmr.result.FloatResult;
import jmr.result.ResultMetadata;
import jmr.result.Vector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import jmr.descriptor.Comparator;
import ui.ComparatorRender;
import ui.TypeComparatorRender;

public class JMRFrame extends javax.swing.JFrame {

    private AttentionClassifier clasificador;
    private boolean dbOpen;
    private ArrayList<LabelGroup> listLabelGroup;
    private Map<Integer, String> classMap = new HashMap<>();
    private RegionLabelDescriptor.IoUComparator iouComparator= new RegionLabelDescriptor.IoUComparator();

    private final String BASE_PATH_CODE_PYTHON = "/home/laura/GitHub/LRIUAP/";//"/Users/mirismr/MEGAsync/Universidad/Master/TFM_ALL/TFM/";
    private final String BASE_PATH_DBS = "/home/laura/GitHub/LRIUAP/Prototipo/JMR.Application/bds/";///Users/mirismr/MEGAsync/Universidad/Master/TFM_ALL/Prototipo/JMR.Application/bds/";
    private final String PATH_FILE_CLASS_RCNN = BASE_PATH_CODE_PYTHON + "json/coco_class_index.json";

    /**
     * Crea una ventana principal
     */
    public JMRFrame() {
        initComponents();
        setIconImage((new ImageIcon(getClass().getResource("/icons/iconoJMR.png"))).getImage());

        //Desactivamos botonos de BD
        this.botonCloseDB.setEnabled(false);
        this.botonSaveDB.setEnabled(false);
        this.botonAddRecordDB.setEnabled(false);
        this.botonSearchDB.setEnabled(false);
        
        //this.botonCompara.setVisible(false);
        

        this.actualizaModeloCargado(false);
        this.dbOpen = false;
        this.spinnerArea.setVisible(false);

        // load classes from .json
        this.loadClasses();

        //close connection with tcp server when press x button
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if(clasificador!=null)
                    clasificador.closeConnection();
                System.exit(0);
            }
        });

    }

    /**
     * Devuelve la ventana interna seleccionada de tipo imagen (null si no
     * hubiese ninguna selecionada o si fuese de otro tipo)
     *
     * @return la ventana interna seleccionada de tipo imagen
     */
    public JMRImageInternalFrame getSelectedImageFrame() {
        JInternalFrame vi = escritorio.getSelectedFrame();
        if (vi instanceof JMRImageInternalFrame) {
            return (JMRImageInternalFrame) escritorio.getSelectedFrame();
        } else {
            return null;
        }
    }

    /**
     * Devuelve la imagen de la ventana interna selecionada
     *
     * @return la imagen seleccionada
     */
    private BufferedImage getSelectedImage() {
        BufferedImage img = null;
        ImageInternalFrame vi = this.getSelectedImageFrame();
        if (vi != null) {
            if (vi.getType() == ImageInternalFrame.TYPE_STANDAR) {
                img = vi.getImage();
            } else {
                JOptionPane.showInternalMessageDialog(escritorio, "An image must be selected", "Image", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        return img;
    }

    /**
     * Devuelve el título de la ventana interna selecionada
     *
     * @return el título de la ventana interna selecionada
     */
    private String getSelectedFrameTitle() {
        String title = "";
        JInternalFrame vi = escritorio.getSelectedFrame();
        if (vi != null) {
            title = vi.getTitle();
        }
        return title;
    }

    /**
     * Sitúa la ventana interna <tt>vi</tt> debajo de la ventana interna activa
     * y con el mismo tamaño.
     *
     * @param vi la ventana interna
     */
    private void locateInternalFrame(JInternalFrame vi) {
        JInternalFrame vSel = escritorio.getSelectedFrame();
        if (vSel != null) {
            vi.setLocation(vSel.getX() + 20, vSel.getY() + 20);
            vi.setSize(vSel.getSize());
        }
    }

    /**
     * Muestra la ventana interna <tt>vi</tt>
     *
     * @param vi la ventana interna
     */
    private void showInternalFrame(JInternalFrame vi) {
        if (vi instanceof ImageInternalFrame) {
            ((ImageInternalFrame) vi).setGrid(this.verGrid.isSelected());
            ((ImageInternalFrame) vi).addPixelListener(new ManejadorPixel());
        }
        this.locateInternalFrame(vi);
        this.escritorio.add(vi);
        vi.setVisible(true);
    }

    /**
     * Clase interna manejadora de eventos de pixel
     */
    private class ManejadorPixel implements PixelListener {

        /**
         * Gestiona el cambio de localización del pixel activo, actualizando la
         * información de la barra de tareas.
         *
         * @param evt evento de pixel
         */
        @Override
        public void positionChange(PixelEvent evt) {
            String text = " ";
            Point p = evt.getPixelLocation();
            if (p != null) {
                Color c = evt.getRGB();
                Integer alpha = evt.getAlpha();
                text = "(" + p.x + "," + p.y + ") : [" + c.getRed() + "," + c.getGreen() + "," + c.getBlue();
                text += alpha == null ? "]" : ("," + alpha + "]");
            }
            posicionPixel.setText(text);
        }
    }

    /*
     * Código generado por Netbeans para el diseño del interfaz
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        popupMenuPanelOutput = new javax.swing.JPopupMenu();
        clear = new javax.swing.JMenuItem();
        popupMenuSeleccionDescriptores = new javax.swing.JPopupMenu();
        colorDominante = new javax.swing.JRadioButtonMenuItem();
        colorEstructurado = new javax.swing.JRadioButtonMenuItem();
        colorEscalable = new javax.swing.JRadioButtonMenuItem();
        colorMedio = new javax.swing.JRadioButtonMenuItem();
        separadorDescriptores = new javax.swing.JPopupMenu.Separator();
        labelDescriptor = new javax.swing.JRadioButtonMenuItem();
        popupMenuGrid = new javax.swing.JPopupMenu();
        jRadioButtonMenuItem1 = new javax.swing.JRadioButtonMenuItem();
        popupMenuSeleccionDescriptoresDB = new javax.swing.JPopupMenu();
        colorDominanteDB = new javax.swing.JRadioButtonMenuItem();
        colorEstructuradoDB = new javax.swing.JRadioButtonMenuItem();
        colorEscalableDB = new javax.swing.JRadioButtonMenuItem();
        colorMedioDB = new javax.swing.JRadioButtonMenuItem();
        separadorDescriptoresDB = new javax.swing.JPopupMenu.Separator();
        labelDescriptorDB = new javax.swing.JRadioButtonMenuItem();
        popupSeleccionEtiquetasBD = new javax.swing.JPopupMenu();
        splitPanelCentral = new javax.swing.JSplitPane();
        escritorio = new javax.swing.JDesktopPane();
        showPanelInfo = new javax.swing.JLabel();
        panelTabuladoInfo = new javax.swing.JTabbedPane();
        panelOutput = new javax.swing.JPanel();
        scrollEditorOutput = new javax.swing.JScrollPane();
        editorOutput = new javax.swing.JEditorPane();
        panelBarraHerramientas = new javax.swing.JPanel();
        barraArchivo = new javax.swing.JToolBar();
        botonAbrir = new javax.swing.JButton();
        botonGuardar = new javax.swing.JButton();
        buttonLoadModel = new javax.swing.JButton();
        barraBD = new javax.swing.JToolBar();
        botonNewDB = new javax.swing.JButton();
        botonOpenDB = new javax.swing.JButton();
        botonSaveDB = new javax.swing.JButton();
        botonCloseDB = new javax.swing.JButton();
        botonAddRecordDB = new javax.swing.JButton();
        botonSearchDB = new javax.swing.JButton();
        jToolBar1 = new javax.swing.JToolBar();
        botonCompara = new javax.swing.JButton();
        barraParametrosConsulta = new javax.swing.JToolBar();
        buttonExplore = new javax.swing.JButton();
        spinnerArea = new javax.swing.JSpinner();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        jToolBar2 = new javax.swing.JToolBar();
        comboBoxClasesCargadasRCNN = new javax.swing.JComboBox<>();
        botonAniadirGrupoLabelsRCNN = new javax.swing.JButton();
        comboboxAllComparators = new javax.swing.JComboBox<>();
        comboboxComparatorIoU = new javax.swing.JComboBox<>();
        comboboxAggregationType = new javax.swing.JComboBox<>();
        checkBoxInclusion = new javax.swing.JCheckBox();
        checkBoxConsultaLabel = new javax.swing.JCheckBox();
        barraEstado = new javax.swing.JPanel();
        posicionPixel = new javax.swing.JLabel();
        infoDB = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        menuArchivo = new javax.swing.JMenu();
        menuAbrir = new javax.swing.JMenuItem();
        menuGuardar = new javax.swing.JMenuItem();
        separador1 = new javax.swing.JPopupMenu.Separator();
        closeAll = new javax.swing.JMenuItem();
        menuVer = new javax.swing.JMenu();
        verGrid = new javax.swing.JCheckBoxMenuItem();
        usarTransparencia = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        showResized = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        menuZoom = new javax.swing.JMenu();
        menuZoomIn = new javax.swing.JMenuItem();
        menuZoomOut = new javax.swing.JMenuItem();

        popupMenuPanelOutput.setAlignmentY(0.0F);
        popupMenuPanelOutput.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        clear.setText("Clear");
        clear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearActionPerformed(evt);
            }
        });
        popupMenuPanelOutput.add(clear);

        colorDominante.setText("Dominant color");
        popupMenuSeleccionDescriptores.add(colorDominante);

        colorEstructurado.setText("Structured color");
        popupMenuSeleccionDescriptores.add(colorEstructurado);

        colorEscalable.setText("Scalable color");
        popupMenuSeleccionDescriptores.add(colorEscalable);

        colorMedio.setText("Mean color");
        popupMenuSeleccionDescriptores.add(colorMedio);
        popupMenuSeleccionDescriptores.add(separadorDescriptores);

        labelDescriptor.setSelected(true);
        labelDescriptor.setText("Label");
        popupMenuSeleccionDescriptores.add(labelDescriptor);

        jRadioButtonMenuItem1.setSelected(true);
        jRadioButtonMenuItem1.setText("jRadioButtonMenuItem1");
        popupMenuGrid.add(jRadioButtonMenuItem1);

        colorDominanteDB.setText("Dominant color");
        colorDominanteDB.setEnabled(false);
        popupMenuSeleccionDescriptoresDB.add(colorDominanteDB);

        colorEstructuradoDB.setText("Structured color");
        popupMenuSeleccionDescriptoresDB.add(colorEstructuradoDB);

        colorEscalableDB.setText("Scalable color");
        popupMenuSeleccionDescriptoresDB.add(colorEscalableDB);

        colorMedioDB.setText("Mean color");
        popupMenuSeleccionDescriptoresDB.add(colorMedioDB);
        popupMenuSeleccionDescriptoresDB.add(separadorDescriptoresDB);

        labelDescriptorDB.setSelected(true);
        labelDescriptorDB.setText("Label");
        popupMenuSeleccionDescriptoresDB.add(labelDescriptorDB);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Detecting Objects");
        setName("ventanaPrincipal"); // NOI18N

        splitPanelCentral.setDividerLocation(1.0);
        splitPanelCentral.setDividerSize(3);
        splitPanelCentral.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPanelCentral.setPreferredSize(new java.awt.Dimension(0, 0));
        splitPanelCentral.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                splitPanelCentralPropertyChange(evt);
            }
        });

        escritorio.setBackground(java.awt.Color.lightGray);
        escritorio.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        showPanelInfo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/desplegar20.png"))); // NOI18N
        showPanelInfo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                showPanelInfoMousePressed(evt);
            }
        });

        escritorio.setLayer(showPanelInfo, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout escritorioLayout = new javax.swing.GroupLayout(escritorio);
        escritorio.setLayout(escritorioLayout);
        escritorioLayout.setHorizontalGroup(
            escritorioLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, escritorioLayout.createSequentialGroup()
                .addGap(0, 1218, Short.MAX_VALUE)
                .addComponent(showPanelInfo))
        );
        escritorioLayout.setVerticalGroup(
            escritorioLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, escritorioLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(showPanelInfo))
        );

        splitPanelCentral.setTopComponent(escritorio);

        panelTabuladoInfo.setMinimumSize(new java.awt.Dimension(0, 0));
        panelTabuladoInfo.setPreferredSize(new java.awt.Dimension(0, 0));

        panelOutput.setMinimumSize(new java.awt.Dimension(0, 0));
        panelOutput.setPreferredSize(new java.awt.Dimension(0, 0));
        panelOutput.setLayout(new java.awt.BorderLayout());

        scrollEditorOutput.setBorder(null);
        scrollEditorOutput.setMinimumSize(new java.awt.Dimension(0, 0));

        editorOutput.setBorder(null);
        editorOutput.setMinimumSize(new java.awt.Dimension(0, 0));
        editorOutput.setPreferredSize(new java.awt.Dimension(0, 0));
        editorOutput.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                editorOutputMouseReleased(evt);
            }
        });
        scrollEditorOutput.setViewportView(editorOutput);

        panelOutput.add(scrollEditorOutput, java.awt.BorderLayout.CENTER);

        panelTabuladoInfo.addTab("Output", panelOutput);

        splitPanelCentral.setBottomComponent(panelTabuladoInfo);

        getContentPane().add(splitPanelCentral, java.awt.BorderLayout.CENTER);

        panelBarraHerramientas.setAlignmentX(0.0F);
        panelBarraHerramientas.setAlignmentY(0.0F);
        panelBarraHerramientas.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        barraArchivo.setRollover(true);
        barraArchivo.setAlignmentX(0.0F);

        botonAbrir.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/open24.png"))); // NOI18N
        botonAbrir.setToolTipText("Open");
        botonAbrir.setFocusable(false);
        botonAbrir.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonAbrir.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonAbrir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonAbrirActionPerformed(evt);
            }
        });
        barraArchivo.add(botonAbrir);

        botonGuardar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/save24.png"))); // NOI18N
        botonGuardar.setToolTipText("Save");
        botonGuardar.setFocusable(false);
        botonGuardar.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonGuardar.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonGuardar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonGuardarActionPerformed(evt);
            }
        });
        barraArchivo.add(botonGuardar);

        buttonLoadModel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/brain24_color.png"))); // NOI18N
        buttonLoadModel.setToolTipText("Label image");
        buttonLoadModel.setComponentPopupMenu(popupMenuSeleccionDescriptores);
        buttonLoadModel.setFocusable(false);
        buttonLoadModel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        buttonLoadModel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        buttonLoadModel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonLoadModelActionPerformed(evt);
            }
        });
        barraArchivo.add(buttonLoadModel);

        panelBarraHerramientas.add(barraArchivo);

        barraBD.setRollover(true);

        botonNewDB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/database.png"))); // NOI18N
        botonNewDB.setToolTipText("Create a new database");
        botonNewDB.setBorderPainted(false);
        botonNewDB.setComponentPopupMenu(popupMenuSeleccionDescriptoresDB);
        botonNewDB.setFocusable(false);
        botonNewDB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonNewDB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonNewDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonNewDBActionPerformed(evt);
            }
        });
        barraBD.add(botonNewDB);

        botonOpenDB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/openDB.png"))); // NOI18N
        botonOpenDB.setToolTipText("Open a database");
        botonOpenDB.setFocusable(false);
        botonOpenDB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonOpenDB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonOpenDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonOpenDBActionPerformed(evt);
            }
        });
        barraBD.add(botonOpenDB);

        botonSaveDB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/saveDB.png"))); // NOI18N
        botonSaveDB.setToolTipText("Save the database");
        botonSaveDB.setFocusable(false);
        botonSaveDB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonSaveDB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonSaveDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonSaveDBActionPerformed(evt);
            }
        });
        barraBD.add(botonSaveDB);

        botonCloseDB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/deleteBD.png"))); // NOI18N
        botonCloseDB.setToolTipText("Close the database");
        botonCloseDB.setFocusable(false);
        botonCloseDB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonCloseDB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonCloseDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonCloseDBActionPerformed(evt);
            }
        });
        barraBD.add(botonCloseDB);

        botonAddRecordDB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/addBD.png"))); // NOI18N
        botonAddRecordDB.setToolTipText("");
        botonAddRecordDB.setFocusable(false);
        botonAddRecordDB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonAddRecordDB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonAddRecordDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonAddRecordDBActionPerformed(evt);
            }
        });
        barraBD.add(botonAddRecordDB);

        botonSearchDB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/seacrhDB.png"))); // NOI18N
        botonSearchDB.setFocusable(false);
        botonSearchDB.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonSearchDB.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonSearchDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonSearchDBActionPerformed(evt);
            }
        });
        barraBD.add(botonSearchDB);

        jToolBar1.setRollover(true);

        botonCompara.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/compare24.png"))); // NOI18N
        botonCompara.setToolTipText("Compare");
        botonCompara.setComponentPopupMenu(popupMenuSeleccionDescriptores);
        botonCompara.setFocusable(false);
        botonCompara.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonCompara.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonCompara.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonComparaActionPerformed(evt);
            }
        });
        jToolBar1.add(botonCompara);

        barraBD.add(jToolBar1);

        panelBarraHerramientas.add(barraBD);

        barraParametrosConsulta.setRollover(true);

        buttonExplore.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/binoculars.png"))); // NOI18N
        buttonExplore.setFocusable(false);
        buttonExplore.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        buttonExplore.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        buttonExplore.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonExploreActionPerformed(evt);
            }
        });
        barraParametrosConsulta.add(buttonExplore);

        spinnerArea.setValue(60);
        spinnerArea.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerAreaStateChanged(evt);
            }
        });
        barraParametrosConsulta.add(spinnerArea);
        barraParametrosConsulta.add(jSeparator3);

        panelBarraHerramientas.add(barraParametrosConsulta);

        jToolBar2.setRollover(true);
        jToolBar2.setPreferredSize(new java.awt.Dimension(100, 37));

        jToolBar2.add(comboBoxClasesCargadasRCNN);

        panelBarraHerramientas.add(jToolBar2);

        botonAniadirGrupoLabelsRCNN.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/etiquetaAdd.png"))); // NOI18N
        botonAniadirGrupoLabelsRCNN.setFocusable(false);
        botonAniadirGrupoLabelsRCNN.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        botonAniadirGrupoLabelsRCNN.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        botonAniadirGrupoLabelsRCNN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                botonAniadirGrupoLabelsRCNNActionPerformed(evt);
            }
        });
        panelBarraHerramientas.add(botonAniadirGrupoLabelsRCNN);

        comboboxAllComparators.setModel(new DefaultComboBoxModel<Comparator>(new Comparator[]{new LabelDescriptor.EqualComparator(),new LabelDescriptor.InclusionComparator(),new LabelDescriptor.SoftEqualComparator(),iouComparator,new LabelDescriptor.WeightBasedComparator()}));
        comboboxAllComparators.setSelectedIndex(3);
        comboboxAllComparators.setPreferredSize(new java.awt.Dimension(100, 35));
        comboboxAllComparators.setRenderer(ComparatorRender.getInstance());
        comboboxAllComparators.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboboxAllComparatorsActionPerformed(evt);
            }
        });
        panelBarraHerramientas.add(comboboxAllComparators);

        comboboxComparatorIoU.setModel(new DefaultComboBoxModel<Comparator>(new Comparator[]{new LabelDescriptor.EqualComparator(),new LabelDescriptor.InclusionComparator()}));
        comboboxComparatorIoU.setRenderer(ComparatorRender.getInstance());
        comboboxComparatorIoU.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboboxComparatorIoUActionPerformed(evt);
            }
        });
        panelBarraHerramientas.add(comboboxComparatorIoU);

        comboboxAggregationType.setModel(new DefaultComboBoxModel<Integer>(new Integer[]{1,2,3,4}));
        comboboxAggregationType.setRenderer(TypeComparatorRender.getInstance());
        comboboxAggregationType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboboxAggregationTypeActionPerformed(evt);
            }
        });
        panelBarraHerramientas.add(comboboxAggregationType);

        checkBoxInclusion.setText("Inclusion");
        checkBoxInclusion.setFocusable(false);
        checkBoxInclusion.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        checkBoxInclusion.setMaximumSize(new java.awt.Dimension(80, 46));
        checkBoxInclusion.setPreferredSize(new java.awt.Dimension(80, 46));
        checkBoxInclusion.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        panelBarraHerramientas.add(checkBoxInclusion);

        checkBoxConsultaLabel.setText("Label query");
        checkBoxConsultaLabel.setFocusable(false);
        checkBoxConsultaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        checkBoxConsultaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        panelBarraHerramientas.add(checkBoxConsultaLabel);

        getContentPane().add(panelBarraHerramientas, java.awt.BorderLayout.PAGE_START);

        barraEstado.setLayout(new java.awt.BorderLayout());

        posicionPixel.setText("  ");
        barraEstado.add(posicionPixel, java.awt.BorderLayout.LINE_START);

        infoDB.setText("Not open");
        barraEstado.add(infoDB, java.awt.BorderLayout.EAST);

        getContentPane().add(barraEstado, java.awt.BorderLayout.SOUTH);

        menuArchivo.setText("File");

        menuAbrir.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/open16.png"))); // NOI18N
        menuAbrir.setText("Open");
        menuAbrir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuAbrirActionPerformed(evt);
            }
        });
        menuArchivo.add(menuAbrir);

        menuGuardar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/save16.png"))); // NOI18N
        menuGuardar.setText("Save");
        menuGuardar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuGuardarActionPerformed(evt);
            }
        });
        menuArchivo.add(menuGuardar);
        menuArchivo.add(separador1);

        closeAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/closeall16.png"))); // NOI18N
        closeAll.setText("Close all");
        closeAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeAllActionPerformed(evt);
            }
        });
        menuArchivo.add(closeAll);

        menuBar.add(menuArchivo);

        menuVer.setText("View");

        verGrid.setSelected(true);
        verGrid.setText("Show grid");
        verGrid.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                verGridActionPerformed(evt);
            }
        });
        menuVer.add(verGrid);

        usarTransparencia.setSelected(true);
        usarTransparencia.setText("Use transparency");
        menuVer.add(usarTransparencia);
        menuVer.add(jSeparator2);

        showResized.setText("Show resized images");
        menuVer.add(showResized);
        menuVer.add(jSeparator1);

        menuZoom.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/zoom16.png"))); // NOI18N
        menuZoom.setText("Zoom");

        menuZoomIn.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, 0));
        menuZoomIn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/zoom-in16.png"))); // NOI18N
        menuZoomIn.setText("Zoom in");
        menuZoomIn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuZoomInActionPerformed(evt);
            }
        });
        menuZoom.add(menuZoomIn);

        menuZoomOut.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, 0));
        menuZoomOut.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/zoom-out16.png"))); // NOI18N
        menuZoomOut.setText("Zoom out");
        menuZoomOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuZoomOutActionPerformed(evt);
            }
        });
        menuZoom.add(menuZoomOut);

        menuVer.add(menuZoom);

        menuBar.add(menuVer);

        setJMenuBar(menuBar);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void menuAbrirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuAbrirActionPerformed
        BufferedImage img;
        File directorioBD = new File(BASE_PATH_CODE_PYTHON);
        JFileChooser dlg = new JFileChooser(directorioBD);
        dlg.setMultiSelectionEnabled(true);
        int resp = dlg.showOpenDialog(this);
        if (resp == JFileChooser.APPROVE_OPTION) {
            try {
                File files[] = dlg.getSelectedFiles();
                for (File f : files) {
                    img = ImageIO.read(f);
                    if (img != null) {
                        ImageInternalFrame vi = new JMRImageInternalFrame(this, img, f.toURI().toURL());
                        vi.setTitle(f.getName());
                        this.showInternalFrame(vi);
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showInternalMessageDialog(escritorio, "Error in image opening", "Image", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }//GEN-LAST:event_menuAbrirActionPerformed

    private void menuGuardarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuGuardarActionPerformed
        BufferedImage img = this.getSelectedImage();
        if (img != null) {
            JFileChooser dlg = new JFileChooser();
            int resp = dlg.showSaveDialog(this);
            if (resp == JFileChooser.APPROVE_OPTION) {
                File f = dlg.getSelectedFile();
                try {
                    ImageIO.write(img, "png", f);
                    escritorio.getSelectedFrame().setTitle(f.getName());
                } catch (Exception ex) {
                    JOptionPane.showInternalMessageDialog(escritorio, "Error in image saving", "Image", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }//GEN-LAST:event_menuGuardarActionPerformed

    private void botonAbrirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonAbrirActionPerformed
        this.menuAbrirActionPerformed(evt);
    }//GEN-LAST:event_botonAbrirActionPerformed

    private void botonGuardarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonGuardarActionPerformed
        this.menuGuardarActionPerformed(evt);
    }//GEN-LAST:event_botonGuardarActionPerformed

    private void clearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearActionPerformed
        this.editorOutput.setText("");
    }//GEN-LAST:event_clearActionPerformed

    private void splitPanelCentralPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_splitPanelCentralPropertyChange
        if (evt.getPropertyName().equals("dividerLocation")) {
            float dividerLocation = (float) splitPanelCentral.getDividerLocation() / splitPanelCentral.getMaximumDividerLocation();
            if (dividerLocation >= 1) {//Está colapsada
                //showPanelInfo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/desplegar20.png")));
            } else {
                //showPanelInfo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/cerrar16.png")));
            }
        }
    }//GEN-LAST:event_splitPanelCentralPropertyChange

    private void closeAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeAllActionPerformed
        escritorio.removeAll();
        escritorio.repaint();
    }//GEN-LAST:event_closeAllActionPerformed

    private void menuZoomOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuZoomOutActionPerformed
        ImageInternalFrame vi = this.getSelectedImageFrame();
        if (vi != null) {
            int zoom = vi.getZoom();
            if (zoom >= 2) {
                vi.setZoom(zoom - 1);
                vi.repaint();
            }
        }
    }//GEN-LAST:event_menuZoomOutActionPerformed

    private void menuZoomInActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuZoomInActionPerformed
        ImageInternalFrame vi = this.getSelectedImageFrame();
        if (vi != null) {
            vi.setZoom(vi.getZoom() + 1);
            vi.repaint();
        }
    }//GEN-LAST:event_menuZoomInActionPerformed

    private void verGridActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_verGridActionPerformed
        JInternalFrame ventanas[] = escritorio.getAllFrames();
        for (JInternalFrame vi : ventanas) {
            ((ImageInternalFrame) vi).setGrid(this.verGrid.isSelected());
            vi.repaint();
        }
    }//GEN-LAST:event_verGridActionPerformed

    private void botonComparaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonComparaActionPerformed
        JMRImageInternalFrame viAnalyzed, viQuery = this.getSelectedImageFrame();
        if (viQuery != null) {
            java.awt.Cursor cursor = this.getCursor();
            setCursor(new java.awt.Cursor(java.awt.Cursor.WAIT_CURSOR));

            //Calculamos descriptores en la imagen consulta
            ArrayList<MediaDescriptor> descriptores_query = new ArrayList();
            if (this.colorDominante.isSelected()) {
                MPEG7DominantColors dcd_query = viQuery.getDominantColorDescriptor();
                if (dcd_query == null) {
                    dcd_query = new MPEG7DominantColors();
                    dcd_query.calculate(this.getSelectedImage(), true);
                    viQuery.setDominantColorDescriptor(dcd_query);
                }
                descriptores_query.add(dcd_query);
            }
            if (this.colorEstructurado.isSelected()) {
                JMRExtendedBufferedImage imgJMR = new JMRExtendedBufferedImage(this.getSelectedImage());
                MPEG7ColorStructure dcs_query = new MPEG7ColorStructure(imgJMR);
                descriptores_query.add(dcs_query);
            }
            if (this.colorEscalable.isSelected()) {
                JMRExtendedBufferedImage imgJMR = new JMRExtendedBufferedImage(this.getSelectedImage());
                MPEG7ScalableColor dsc_query = new MPEG7ScalableColor(imgJMR);
                descriptores_query.add(dsc_query);
            }
            if (this.colorMedio.isSelected()) {
                JMRExtendedBufferedImage imgJMR = new JMRExtendedBufferedImage(this.getSelectedImage());
                SingleColorDescriptor dmean_query = new SingleColorDescriptor(imgJMR);
                descriptores_query.add(dmean_query);
            }

            if (this.labelDescriptor.isSelected()) {
                RegionLabelDescriptor ld_query = new RegionLabelDescriptor(viQuery.getURL().getFile(), clasificador);
                //ld_query.setComparator((Comparator)this.comboboxAllComparators.getSelectedItem());
                this.editorOutput.setText(ld_query.toString());
                descriptores_query.add(ld_query);
            }

            //Comparamos la imagen consulta con el resto de imágenes del escritorio                        
            Vector vresult;

            List<ResultMetadata> resultList = new LinkedList<>();

            String text = editorOutput.getText();
            JInternalFrame ventanas[] = escritorio.getAllFrames();
            for (JInternalFrame vi : ventanas) {
                if (vi instanceof JMRImageInternalFrame && ((JMRImageInternalFrame)vi).getURL()!=null) {
                    viAnalyzed = (JMRImageInternalFrame) vi;

                    Iterator<MediaDescriptor> itQuery = descriptores_query.iterator();
                    MediaDescriptor current_descriptor;
                    vresult = new Vector(descriptores_query.size());
                    int index = 0;

                    //DCD
                    if (this.colorDominante.isSelected()) {
                        MPEG7DominantColors dcd_analyzed = viAnalyzed.getDominantColorDescriptor();
                        if (dcd_analyzed == null) {
                            dcd_analyzed = new MPEG7DominantColors();
                            dcd_analyzed.calculate(viAnalyzed.getImage(), true);
                            viAnalyzed.setDominantColorDescriptor(dcd_analyzed);
                        }
                        current_descriptor = itQuery.next();
                        FloatResult result = (FloatResult) current_descriptor.compare(dcd_analyzed);
                        vresult.setCoordinate(index++, result.toDouble());
                    }
                    //CSD
                    if (this.colorEstructurado.isSelected()) {
                        JMRExtendedBufferedImage imgJMR = new JMRExtendedBufferedImage(viAnalyzed.getImage());
                        MPEG7ColorStructure dcs_analyzed = new MPEG7ColorStructure(imgJMR);
                        current_descriptor = itQuery.next();
                        Double result = (Double) current_descriptor.compare(dcs_analyzed);
                        vresult.setCoordinate(index++, result);
                    }
                    //SCD
                    if (this.colorEscalable.isSelected()) {
                        JMRExtendedBufferedImage imgJMR = new JMRExtendedBufferedImage(viAnalyzed.getImage());
                        MPEG7ScalableColor dsc_analyzed = new MPEG7ScalableColor(imgJMR);
                        current_descriptor = itQuery.next();
                        Double result = (Double) current_descriptor.compare(dsc_analyzed);
                        vresult.setCoordinate(index++, result);
                    }
                    // Mean color
                    if (this.colorMedio.isSelected()) {
                        JMRExtendedBufferedImage imgJMR = new JMRExtendedBufferedImage(viAnalyzed.getImage());
                        SingleColorDescriptor dmean_analyzed = new SingleColorDescriptor(imgJMR);
                        current_descriptor = itQuery.next();
                        Double compare = (Double) current_descriptor.compare(dmean_analyzed);
                        FloatResult result = new FloatResult(compare.floatValue());
                        vresult.setCoordinate(index++, result.toDouble());
                    }

                    if (this.labelDescriptor.isSelected()) {
                        RegionLabelDescriptor ld_analyzed = new RegionLabelDescriptor(viAnalyzed.getURL().getFile(), clasificador);
                        //ld_analyzed.setComparator((Comparator)this.comboboxAllComparators.getSelectedItem());
                        current_descriptor = itQuery.next();
                        //current_descriptor.setComparator((Comparator)(this.comboboxAllComparators.getSelectedItem()));
                        Double result = (Double) current_descriptor.compare(ld_analyzed);
                        vresult.setCoordinate(index++, result);
                        this.editorOutput.setText(ld_analyzed.toString());
                    }

                    resultList.add(new ResultMetadata(vresult, viAnalyzed.getImage()));
                    text += "\nDist(" + viQuery.getTitle() + "," + viAnalyzed.getTitle() + ") = ";
                    text += vresult != null ? vresult.toString() + "\n" : "No calculado\n";
                }
            }
            this.editorOutput.setText(this.editorOutput.getText() + text);
            setCursor(cursor);
            //Creamas la ventana interna con los resultados
            resultList.sort(null);
            ImageListInternalFrame listFrame = new ImageListInternalFrame(resultList);
            this.escritorio.add(listFrame);
            listFrame.setTitle(this.textTitle());
            listFrame.setVisible(true);
        }
    }//GEN-LAST:event_botonComparaActionPerformed

    private void setDataBaseButtonStatus(boolean closed) {
        this.botonNewDB.setEnabled(closed);
        this.botonOpenDB.setEnabled(closed);
        this.botonCloseDB.setEnabled(!closed);
        this.botonSaveDB.setEnabled(!closed);
        this.botonAddRecordDB.setEnabled(!closed);
        this.botonSearchDB.setEnabled(!closed);
    }

    private Class[] getDBDescriptorClasses() {
        ArrayList<Class> outputL = new ArrayList<>();
        if (this.colorEstructuradoDB.isSelected()) {
            outputL.add(MPEG7ColorStructure.class);
        }
        if (this.colorEscalableDB.isSelected()) {
            outputL.add(MPEG7ScalableColor.class);
        }
        if (this.colorMedioDB.isSelected()) {
            outputL.add(SingleColorDescriptor.class);
        }
        if (this.labelDescriptorDB.isSelected()) {
            outputL.add(RegionLabelDescriptor.class);
        }
        Class output[] = new Class[outputL.size()];
        for (int i = 0; i < outputL.size(); i++) {
            output[i] = outputL.get(i);
        }
        return output;
    }

    private void botonNewDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonNewDBActionPerformed
        // Creamos la base de datos vacía
        database = new ListDB(getDBDescriptorClasses());
        // Activamos/desactivamos botones
        setDataBaseButtonStatus(false);
        this.dbOpen = true;

        updateInfoDBStatusBar("New DB (not saved)");
    }//GEN-LAST:event_botonNewDBActionPerformed

    private void botonCloseDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonCloseDBActionPerformed
        database.clear();
        database = null;
        // Activamos/desactivamos botones
        setDataBaseButtonStatus(true);
        this.dbOpen = false;

        updateInfoDBStatusBar(null);
    }//GEN-LAST:event_botonCloseDBActionPerformed

    private void botonAddRecordDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonAddRecordDBActionPerformed

        LabelDescriptor.setDefaultClassifier(clasificador);

        if (database != null) {
            java.awt.Cursor cursor = this.getCursor();
            setCursor(new java.awt.Cursor(java.awt.Cursor.WAIT_CURSOR));
            //Incorporamos a la BD todas las imágenes del escritorio
            JInternalFrame ventanas[] = escritorio.getAllFrames();
            JMRImageInternalFrame viAnalyzed;
            for (JInternalFrame vi : ventanas) {
                if (vi instanceof JMRImageInternalFrame) {
                    viAnalyzed = (JMRImageInternalFrame) vi;
                    if(this.labelDescriptorDB.isSelected())
                        database.add(viAnalyzed.getURL().getFile(), viAnalyzed.getURL());
                    else
                        database.add(((JMRImageInternalFrame) vi).getImage());
                }
            }
            setCursor(cursor);
            updateInfoDBStatusBar("Updated DB (not saved)");
        }
    }//GEN-LAST:event_botonAddRecordDBActionPerformed

    private void botonSearchDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonSearchDBActionPerformed
        if (database != null) {
            java.awt.Cursor cursor = this.getCursor();
            setCursor(new java.awt.Cursor(java.awt.Cursor.WAIT_CURSOR));
            //RegionLabelDescriptor.setDefaultWeightComparator(new WeightBasedComparator(WeightBasedComparator.TYPE_MIN, this.checkBoxInclusion.isSelected()));
            //RegionLabelDescriptor.setDefaultComparator(new WeightBasedComparator(WeightBasedComparator.TYPE_MIN, this.checkBoxInclusion.isSelected()));

            if (!this.checkBoxConsultaLabel.isSelected()) {
                String pathImg = this.getSelectedImageFrame().getURL().getFile();
                if (pathImg != null) {

                    LabelDescriptor.setDefaultClassifier(clasificador);

                    ListDB.Record record;
                    if(this.labelDescriptorDB.isSelected())
                        record= database.new Record(pathImg);
                    else
                        record=database.new Record(this.getSelectedImage());
                    //record.setComparator(this.descriptorLCompa);
                    List<ResultMetadata> queryResult = database.queryMetadata(record);
                    ImageListInternalFrame listFrame = new ImageListInternalFrame();
                    String text = this.editorOutput.getText();
                    MediaDescriptor inicial = record.get(0);
                    text += "Imagen consulta: " + inicial.toString() + "\n";
                    for (ResultMetadata r : queryResult) {
                        text += r.getMetadata().toString().trim() + ": ";
                        text += r.getResult().toString() + "\n";
                        ListDB.Record rec = (ListDB.Record) r.getMetadata();
                        if(this.labelDescriptorDB.isSelected())
                            listFrame.add(rec.getLocator(), (String) r.getResult().toString());
                        else
                            listFrame.add((BufferedImage)rec.getSource(),(String) r.getResult().toString());
                        
                    }

                    this.editorOutput.setText(text);
                    this.escritorio.add(listFrame);
                    listFrame.setTitle(this.textTitle());
                    listFrame.setVisible(true);
                    setCursor(cursor);
                }
            } //consulta por label
            else {
                int indiceSeleccionado = this.comboBoxClasesCargadasRCNN.getSelectedIndex();
                LabelGroup lgSeleccionado = this.listLabelGroup.get(indiceSeleccionado);
                String queryLabel[] = (String[]) lgSeleccionado.getLabels().toArray(new String[0]);
                String first = "";
                RegionLabelDescriptor queryDescriptor = null;
                if (queryLabel.length > 1) {
                    first = queryLabel[0];
                    String[] aux = new String[queryLabel.length - 1];
                    for (int i = 1; i < queryLabel.length; i++) {
                        aux[i - 1] = queryLabel[i];
                    }
                    queryLabel = aux;
                    queryDescriptor = new RegionLabelDescriptor(first, queryLabel);
                } else {
                    queryDescriptor = new RegionLabelDescriptor(queryLabel[0], new String[0]);
                }

                Double weights[] = new Double[queryDescriptor.size()];
                ArrayList<ArrayList<Point2D>> points=new ArrayList();
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = 1.0;
                    ArrayList<Point2D> p=new ArrayList();
                    p.add(new Point2D.Double(Double.valueOf(i),0));
                    points.add(p); 
                }
                
                queryDescriptor.setWeights(weights);

                DescriptorList<String> dList = new DescriptorList(null);
                dList.add(queryDescriptor);
                ListDB.Record record = database.new Record(dList);
                //record.setComparator((Comparator)this.comboboxAllComparators.getSelectedItem());

                List<ResultMetadata> queryResult = database.queryMetadata(record);
                ImageListInternalFrame listFrame = new ImageListInternalFrame();
                String text = this.editorOutput.getText();
                for (ResultMetadata r : queryResult) {
                    ListDB.Record rec = (ListDB.Record) r.getMetadata();
                    text += r.getMetadata().toString().trim() + ": ";
                    text += r.getResult().toString() + "\n";
                    listFrame.add(rec.getLocator(), (String) r.getResult().toString());
                }

                this.editorOutput.setText(text);
                this.escritorio.add(listFrame);
                listFrame.setTitle(this.textTitle());
                listFrame.setVisible(true);
                setCursor(cursor);
            }
        }
    }//GEN-LAST:event_botonSearchDBActionPerformed

    private String textTitle(){
        String title="Result ";
        if(this.comboboxAllComparators.isEnabled()){
            String className=this.comboboxAllComparators.getSelectedItem().getClass().getSimpleName();
            title="with "+className;
            if(this.comboboxAggregationType.isEnabled()){
                title+=" "+((TypeComparatorRender)this.comboboxAggregationType.getRenderer()).getLabel();
                if(className.equals("IoUComparator"))
                    title+=" "+this.comboboxComparatorIoU.getSelectedItem().getClass().getSimpleName();
                else if(this.checkBoxInclusion.isSelected())
                    title+=" Inclusion";
                else title+=" Equal";
            }
        }
        return title;
    }
    
    
    private void updateInfoDBStatusBar(String fichero) {
        String infoDB = "Not open";
        if (database != null) {
            infoDB = fichero + " [#" + database.size() + "] [";
            for (Class c : database.getDescriptorClasses()) {
                infoDB += c.getSimpleName() + ",";
            }
            infoDB = infoDB.substring(0, infoDB.length() - 1) + "]";
        }
        this.infoDB.setText(infoDB);
    }

    private void botonOpenDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonOpenDBActionPerformed
        File directorioBD = new File(BASE_PATH_DBS);
        JFileChooser dlg = new JFileChooser(directorioBD);
        dlg.setMultiSelectionEnabled(true);
        int resp = dlg.showOpenDialog(this);
        if (resp == JFileChooser.APPROVE_OPTION) {
            java.awt.Cursor cursor = this.getCursor();
            setCursor(new java.awt.Cursor(java.awt.Cursor.WAIT_CURSOR));
            File file = dlg.getSelectedFile();
            try {
                database = ListDB.open(file);
                setDataBaseButtonStatus(false);
                this.dbOpen = true;

                updateInfoDBStatusBar(file.getName());
            } catch (IOException | ClassNotFoundException ex) {
                System.err.println(ex);
            }
            setCursor(cursor);
        }
    }//GEN-LAST:event_botonOpenDBActionPerformed

    private void botonSaveDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonSaveDBActionPerformed
        //File file = new File(BASE_PATH_DBS + "prueba.jmr.db");
        try {
            JFileChooser dlg = new JFileChooser(new File(BASE_PATH_DBS));
            int resp = dlg.showSaveDialog(this);
            if(resp==JFileChooser.APPROVE_OPTION){
                File file=dlg.getSelectedFile();
                java.awt.Cursor cursor = this.getCursor();
                setCursor(new java.awt.Cursor(java.awt.Cursor.WAIT_CURSOR));
                database.save(file);
                setCursor(cursor);
                updateInfoDBStatusBar(file.getName());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println(ex.getLocalizedMessage());
        }
    }//GEN-LAST:event_botonSaveDBActionPerformed

    private void buttonLoadModelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonLoadModelActionPerformed
        File directorioBD = new File(BASE_PATH_CODE_PYTHON);
        JFileChooser dlg = new JFileChooser(directorioBD);
        //dlg.setMultiSelectionEnabled(true);
        int resp = dlg.showOpenDialog(this);
        if (resp == JFileChooser.APPROVE_OPTION) {
            try {
                File f = dlg.getSelectedFile();
                if(this.clasificador!=null)
                    this.clasificador.setModel(f.getAbsolutePath());
                else
                    this.clasificador = new AttentionClassifier(f.getAbsolutePath(),(Integer)this.spinnerArea.getValue());
                
                this.actualizaModeloCargado(true);

            } catch (Exception ex) {
                JOptionPane.showInternalMessageDialog(escritorio, "Error load model", "Image", JOptionPane.INFORMATION_MESSAGE);
                this.actualizaModeloCargado(false);
            }
        }
    }//GEN-LAST:event_buttonLoadModelActionPerformed

    private void actualizaModeloCargado(boolean status) {
        this.comboBoxClasesCargadasRCNN.setEnabled(status);
        this.comboboxAllComparators.setEnabled(status);
        this.botonAniadirGrupoLabelsRCNN.setEnabled(status);
        this.checkBoxConsultaLabel.setEnabled(status);
        this.checkBoxInclusion.setEnabled(status);
        this.buttonExplore.setEnabled(status);
        this.botonSearchDB.setEnabled(status && this.dbOpen);
        this.actualizaComporatorsDisponibles();
    }
    
    private void actualizaComporatorsDisponibles(){ 
        this.comboboxComparatorIoU.setEnabled(
            this.comboboxAllComparators.isEnabled() &&
            this.comboboxAllComparators.getSelectedItem().getClass().getSimpleName().equals("IoUComparator")
        );
        this.comboboxAggregationType.setEnabled(
            this.comboboxAllComparators.isEnabled() && (
            this.comboboxAllComparators.getSelectedItem().getClass().getSimpleName().equals("IoUComparator")
            ||
            this.comboboxAllComparators.getSelectedItem().getClass().getSimpleName().equals("WeightBasedComparator")
                    
        ));
    }

    private void loadClasses() {
        this.listLabelGroup = new ArrayList<>();

        try {
            JSONParser parser = new JSONParser();
            JSONObject content = (JSONObject) parser.parse(new FileReader(PATH_FILE_CLASS_RCNN));
            this.classMap.clear();
            content.keySet().forEach(k -> {
                String key = k.toString();
                String name = (String) content.get(key);
                this.classMap.put(Integer.parseInt(key), name);
            });
        } catch (FileNotFoundException ex) {
            Logger.getLogger(JMRFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JMRFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger(JMRFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

        List<String> clases = new ArrayList();

        this.classMap.values().forEach(k -> {
            clases.add(k);
            //usado para añadir nuevos
            this.listLabelGroup.add(new LabelGroup(new ArrayList<String>(Arrays.asList(k))));
        });

        this.comboBoxClasesCargadasRCNN.setModel(new DefaultComboBoxModel(clases.toArray(new String[0])));
    }
//    private String findKey(String value) {
//        for (Integer key : this.classMap.keySet()) {
//            if (this.classMap.get(key).equals(value)) {
//                return key.toString();
//            }
//        }
//        return "";
//    }
    private void buttonExploreActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonExploreActionPerformed
        JMRImageInternalFrame frameSelected = (JMRImageInternalFrame) this.escritorio.getSelectedFrame();
        String pathImageSelected = frameSelected.getURL().getFile();
        java.awt.Cursor cursor = this.getCursor();
        setCursor(new java.awt.Cursor(java.awt.Cursor.WAIT_CURSOR));
        RegionLabelDescriptor ld_query = new RegionLabelDescriptor(pathImageSelected, this.clasificador);
        //ld_query.setComparator((Comparator)this.comboboxAllComparators.getSelectedItem());
        setCursor(cursor);
        Random rand = new Random();

        List<String> labels = new ArrayList();
        List<Double> weights = new ArrayList();
        for (int i = 0; i < ld_query.size(); i++) {
            labels.add(ld_query.getLabel(i));
            weights.add(ld_query.getWeight(i));
        }
        MultiRegion region=new MultiRegion(
                frameSelected.getImage(),
                ld_query.getShapes(),
                labels,weights,
                ld_query.getBboxs()
            );

        BufferedImage image=region.createImage(true,!ld_query.getBboxs().isEmpty(), true);

        if (labels.size() == 0) {
            labels.add("No classes found");
        }
        LabelSetPanel panelEtiquetas = new LabelSetPanel(labelsPlusWeights(labels,weights));
        JMRImageInternalFrame vi = new JMRImageInternalFrame(this,image);
        vi.add(panelEtiquetas, BorderLayout.EAST);
        this.showInternalFrame(vi);
    }//GEN-LAST:event_buttonExploreActionPerformed

    private List<String> labelsPlusWeights(List<String> l,List<Double>w){
        List<String> plus=new ArrayList();
        for(int i=0; i<l.size() && i<w.size(); i++){
            plus.add(l.get(i)+" : "+w.get(i).toString());
        }
        return plus;
    }
    
    private void botonAniadirGrupoLabelsRCNNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_botonAniadirGrupoLabelsRCNNActionPerformed
        // TODO add your handling code here:
        if (this.listLabelGroup != null) {
            AddGroupDialog dlg = new AddGroupDialog(this, this.listLabelGroup);
            ArrayList<String> seleccionadas = dlg.showDialog();
            LabelGroup nuevoLG = new LabelGroup(seleccionadas);
            this.listLabelGroup.add(nuevoLG);

            DefaultComboBoxModel aModel = (DefaultComboBoxModel) this.comboBoxClasesCargadasRCNN.getModel();
            aModel.addElement((String) nuevoLG.toString());
            this.comboBoxClasesCargadasRCNN.setModel(aModel);
        }
    }//GEN-LAST:event_botonAniadirGrupoLabelsRCNNActionPerformed

    private void editorOutputMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_editorOutputMouseReleased
        if (evt.isPopupTrigger()) {
            Point p = this.scrollEditorOutput.getMousePosition();
            this.popupMenuPanelOutput.show(this.panelOutput, p.x, p.y);
        }
    }//GEN-LAST:event_editorOutputMouseReleased

    private void spinnerAreaStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerAreaStateChanged
        // TODO add your handling code here:
        this.clasificador.setArea((Integer)this.spinnerArea.getValue());
    }//GEN-LAST:event_spinnerAreaStateChanged

    private void comboboxAllComparatorsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboboxAllComparatorsActionPerformed
        // TODO add your handling code here:
        this.actualizaComporatorsDisponibles();
        RegionLabelDescriptor.setRegionDefaultComparator((Comparator)this.comboboxAllComparators.getSelectedItem());
    }//GEN-LAST:event_comboboxAllComparatorsActionPerformed

    private void comboboxAggregationTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboboxAggregationTypeActionPerformed
        // TODO add your handling code here:
        // Asumimos que el WeightBasedComparator es el último.
        boolean WeightBased=false;
        if(this.comboboxAllComparators.getSelectedItem().getClass().getSimpleName().equals("WeightBasedComparator")){
            WeightBased=true;
        }
    
        this.comboboxAllComparators.removeItemAt(this.comboboxAllComparators.getItemCount()-1);
        this.comboboxAllComparators.addItem(
                new LabelDescriptor.WeightBasedComparator(
                        (Integer)this.comboboxAggregationType.getSelectedItem(),
                        this.checkBoxInclusion.isSelected()
                ));
        this.iouComparator.setType((Integer)this.comboboxAggregationType.getSelectedItem());
        if(WeightBased){
            this.comboboxAllComparators.setSelectedIndex(this.comboboxAllComparators.getItemCount()-1);
        }
        RegionLabelDescriptor.setRegionDefaultComparator((Comparator)this.comboboxAllComparators.getSelectedItem());
    }//GEN-LAST:event_comboboxAggregationTypeActionPerformed

    private void comboboxComparatorIoUActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboboxComparatorIoUActionPerformed
        // TODO add your handling code here:
        RegionLabelDescriptor.IoUComparator.setDefaultComparator((Comparator)this.comboboxComparatorIoU.getSelectedItem());
    }//GEN-LAST:event_comboboxComparatorIoUActionPerformed

    private void showPanelInfoMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_showPanelInfoMousePressed
        float dividerLocation = (float) splitPanelCentral.getDividerLocation() / splitPanelCentral.getMaximumDividerLocation();
        if (dividerLocation >= 1) {//Está colapsada
            splitPanelCentral.setDividerLocation(0.8);
        } else {
            splitPanelCentral.setDividerLocation(1.0);
        }
    }//GEN-LAST:event_showPanelInfoMousePressed

    // Variables no generadas automáticamente 
    ListDB<Object> database = null;

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JToolBar barraArchivo;
    private javax.swing.JToolBar barraBD;
    private javax.swing.JPanel barraEstado;
    private javax.swing.JToolBar barraParametrosConsulta;
    private javax.swing.JButton botonAbrir;
    private javax.swing.JButton botonAddRecordDB;
    private javax.swing.JButton botonAniadirGrupoLabelsRCNN;
    private javax.swing.JButton botonCloseDB;
    private javax.swing.JButton botonCompara;
    private javax.swing.JButton botonGuardar;
    private javax.swing.JButton botonNewDB;
    private javax.swing.JButton botonOpenDB;
    private javax.swing.JButton botonSaveDB;
    private javax.swing.JButton botonSearchDB;
    private javax.swing.JButton buttonExplore;
    private javax.swing.JButton buttonLoadModel;
    private javax.swing.JCheckBox checkBoxConsultaLabel;
    private javax.swing.JCheckBox checkBoxInclusion;
    private javax.swing.JMenuItem clear;
    private javax.swing.JMenuItem closeAll;
    private javax.swing.JRadioButtonMenuItem colorDominante;
    private javax.swing.JRadioButtonMenuItem colorDominanteDB;
    private javax.swing.JRadioButtonMenuItem colorEscalable;
    private javax.swing.JRadioButtonMenuItem colorEscalableDB;
    private javax.swing.JRadioButtonMenuItem colorEstructurado;
    private javax.swing.JRadioButtonMenuItem colorEstructuradoDB;
    private javax.swing.JRadioButtonMenuItem colorMedio;
    private javax.swing.JRadioButtonMenuItem colorMedioDB;
    private javax.swing.JComboBox<String> comboBoxClasesCargadasRCNN;
    private javax.swing.JComboBox<Integer> comboboxAggregationType;
    private javax.swing.JComboBox<Comparator> comboboxAllComparators;
    private javax.swing.JComboBox<Comparator> comboboxComparatorIoU;
    private javax.swing.JEditorPane editorOutput;
    private javax.swing.JDesktopPane escritorio;
    private javax.swing.JLabel infoDB;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItem1;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JToolBar jToolBar2;
    private javax.swing.JRadioButtonMenuItem labelDescriptor;
    private javax.swing.JRadioButtonMenuItem labelDescriptorDB;
    private javax.swing.JMenuItem menuAbrir;
    private javax.swing.JMenu menuArchivo;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem menuGuardar;
    public javax.swing.JMenu menuVer;
    private javax.swing.JMenu menuZoom;
    private javax.swing.JMenuItem menuZoomIn;
    private javax.swing.JMenuItem menuZoomOut;
    private javax.swing.JPanel panelBarraHerramientas;
    private javax.swing.JPanel panelOutput;
    private javax.swing.JTabbedPane panelTabuladoInfo;
    private javax.swing.JPopupMenu popupMenuGrid;
    private javax.swing.JPopupMenu popupMenuPanelOutput;
    private javax.swing.JPopupMenu popupMenuSeleccionDescriptores;
    private javax.swing.JPopupMenu popupMenuSeleccionDescriptoresDB;
    private javax.swing.JPopupMenu popupSeleccionEtiquetasBD;
    public javax.swing.JLabel posicionPixel;
    private javax.swing.JScrollPane scrollEditorOutput;
    private javax.swing.JPopupMenu.Separator separador1;
    private javax.swing.JPopupMenu.Separator separadorDescriptores;
    private javax.swing.JPopupMenu.Separator separadorDescriptoresDB;
    private javax.swing.JLabel showPanelInfo;
    private javax.swing.JCheckBoxMenuItem showResized;
    private javax.swing.JSpinner spinnerArea;
    public javax.swing.JSplitPane splitPanelCentral;
    private javax.swing.JCheckBoxMenuItem usarTransparencia;
    private javax.swing.JCheckBoxMenuItem verGrid;
    // End of variables declaration//GEN-END:variables

}
