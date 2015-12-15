package crw.ui.teleop;

import com.madara.KnowledgeBase;
import crw.proxy.BoatProxy;
import edu.cmu.ri.crw.AsyncVehicleServer;
import edu.cmu.ri.crw.FunctionObserver;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import sami.engine.Engine;
import sami.sensor.Observation;
import sami.sensor.ObservationListenerInt;
import sami.sensor.ObserverInt;

/**
 *
 * @author nbb
 */
public class GainsPanel extends JScrollPane implements ObservationListenerInt {

    private static final Logger LOGGER = Logger.getLogger(GainsPanel.class.getName());
    public static final int THRUST_GAINS_AXIS = 0;
    public static final int RUDDER_GAINS_AXIS = 5;
    public static final int WINCH_GAINS_AXIS = 3;
    private JPanel contentP, velMultP, thrustPidP, rudderPidP, winchPidP; //PPI_P;
    private JPanel peakMotorSignal_P;
    public JTextField velocityMultF, winchTF, thrustPTF, thrustITF, thrustDTF, rudderPTF, rudderITF, rudderDTF;
    //public JTextField PPI_PosP_TF, PPI_VelP_TF, PPI_VelI_TF;
    public JTextField peakForwardMotorSignal_TF, peakBackwardMotorSignal_TF;
    public JLabel winchL;
    private DecimalFormat decimalFormat = new DecimalFormat("#.##");
    public JButton applyB;
    public double velocityMult = 0.12, winch, thrustP, thrustI, thrustD, rudderP, rudderI, rudderD;
    //double PPI_PosP, PPI_VelP, PPI_VelI;
    double peakForwardMotorSignal, peakBackwardMotorSignal;
    private BoatProxy activeProxy = null;
    /////////////////////////////////////////////////////////////////////////
    //private AsyncVehicleServer activeVehicle = null;
    KnowledgeBase knowledge;
    private JPanel thrustFractionFixP;
    private JCheckBox thrustFractionFixCB;    
    public JTextField fixThrustFractionTF;
    public boolean thrustFractionIsFixed = false;
    public double thrustFractionFixedValue;
    /////////////////////////////////////////////////////////////////////////
    private ObserverInt activeWinchObserver = null;   
    final private int textFieldWidth = 100;
    
    public GainsPanel(KnowledgeBase knowledge) {        
        super();
        this.knowledge = knowledge;
        velocityMultF = new JTextField(velocityMult + "");
        velocityMultF.setPreferredSize(new Dimension(textFieldWidth, velocityMultF.getPreferredSize().height));
        winchTF = new JTextField("");
        winchTF.setPreferredSize(new Dimension(textFieldWidth, winchTF.getPreferredSize().height));
        thrustPTF = new JTextField("");
        thrustPTF.setPreferredSize(new Dimension(textFieldWidth, thrustPTF.getPreferredSize().height));
        thrustITF = new JTextField("");
        thrustITF.setPreferredSize(new Dimension(textFieldWidth, thrustITF.getPreferredSize().height));
        thrustDTF = new JTextField("");
        thrustDTF.setPreferredSize(new Dimension(textFieldWidth, thrustDTF.getPreferredSize().height));
        rudderPTF = new JTextField("");
        rudderPTF.setPreferredSize(new Dimension(textFieldWidth, rudderPTF.getPreferredSize().height));
        rudderITF = new JTextField("");
        rudderITF.setPreferredSize(new Dimension(textFieldWidth, rudderITF.getPreferredSize().height));
        rudderDTF = new JTextField("");
        rudderDTF.setPreferredSize(new Dimension(textFieldWidth, rudderDTF.getPreferredSize().height));
        
        /*
        PPI_PosP_TF = new JTextField("");
        PPI_PosP_TF.setPreferredSize(new Dimension(50, PPI_PosP_TF.getPreferredSize().height));
        PPI_VelP_TF = new JTextField("");
        PPI_VelP_TF.setPreferredSize(new Dimension(50, PPI_VelP_TF.getPreferredSize().height));
        PPI_VelI_TF = new JTextField("");
        PPI_VelI_TF.setPreferredSize(new Dimension(50, PPI_VelI_TF.getPreferredSize().height));
        */
        
        peakForwardMotorSignal_TF = new JTextField("");
        peakBackwardMotorSignal_TF = new JTextField("");
        peakForwardMotorSignal_TF.setPreferredSize(new Dimension(textFieldWidth, peakForwardMotorSignal_TF.getPreferredSize().height));
        peakBackwardMotorSignal_TF.setPreferredSize(new Dimension(textFieldWidth, peakBackwardMotorSignal_TF.getPreferredSize().height));

        velMultP = new JPanel();
        velMultP.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        velMultP.add(new JLabel("Velocity multiplier:"));
        velMultP.add(velocityMultF);

        thrustPidP = new JPanel();
        thrustPidP.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        thrustPidP.add(new JLabel("Thrust "));
        thrustPidP.add(new JLabel("P:"));
        thrustPidP.add(thrustPTF);
        thrustPidP.add(new JLabel("I:"));
        thrustPidP.add(thrustITF);
        thrustPidP.add(new JLabel("D:"));
        thrustPidP.add(thrustDTF);

        rudderPidP = new JPanel();
        rudderPidP.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        rudderPidP.add(new JLabel("Rudder"));
        rudderPidP.add(new JLabel("P:"));
        rudderPidP.add(rudderPTF);
        rudderPidP.add(new JLabel("I:"));
        rudderPidP.add(rudderITF);
        rudderPidP.add(new JLabel("D:"));
        rudderPidP.add(rudderDTF);
        
        /*
        PPI_P = new JPanel();
        PPI_P.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        PPI_P.add(new JLabel("PPI"));
        PPI_P.add(new JLabel("PosP:"));
        PPI_P.add(PPI_PosP_TF);
        PPI_P.add(new JLabel("VelP:"));
        PPI_P.add(PPI_VelP_TF);
        PPI_P.add(new JLabel("VelI:"));
        PPI_P.add(PPI_VelI_TF);
        */
        
        peakMotorSignal_P = new JPanel();
        peakMotorSignal_P.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        peakMotorSignal_P.add(new JLabel("max FRWD mtr sgnl:"));
        peakMotorSignal_P.add(peakForwardMotorSignal_TF);
        peakMotorSignal_P.add(new JLabel("max BCKWRD mtr sgnl:"));
        peakMotorSignal_P.add(peakBackwardMotorSignal_TF);
        

        winchPidP = new JPanel();
        winchPidP.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        winchL = new JLabel("Winch value: ---");
        winchPidP.add(winchL);
        winchPidP.add(winchTF);

        applyB = new JButton("Apply");
        applyB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                applyFieldValues();
            }
        });
        
        fixThrustFractionTF = new JTextField();
        fixThrustFractionTF.setPreferredSize(new Dimension(50, fixThrustFractionTF.getPreferredSize().height));
        
        thrustFractionFixP = new JPanel();
        thrustFractionFixP.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        thrustFractionFixP.setLayout(new BoxLayout(thrustFractionFixP, BoxLayout.X_AXIS));
        thrustFractionFixCB = new JCheckBox("fix thrust?",false);              
        thrustFractionFixCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fixThrustFraction();
            }
        });
        
        thrustFractionFixP.add(thrustFractionFixCB);
        thrustFractionFixP.add(fixThrustFractionTF);

        contentP = new JPanel();
        contentP.setLayout(new BoxLayout(contentP, BoxLayout.Y_AXIS));
//        contentP.add(velMultP);
        contentP.add(thrustPidP);
        contentP.add(rudderPidP);
        //contentP.add(PPI_P);
        contentP.add(peakMotorSignal_P);
        contentP.add(winchPidP);
        contentP.add(applyB);
        contentP.add(thrustFractionFixP);
        getViewport().add(contentP);
        setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    }

    public double stringToDouble(String text) {
        double ret = Double.NaN;
        try {
            ret = Double.valueOf(text);
        } catch (NumberFormatException ex) {
        }
        return ret;
    }
    
    
    public void fixThrustFraction() {
        if (thrustFractionFixCB.isSelected()) {
            //System.out.println("FIXING thrust fraction...");
            thrustFractionIsFixed = true;
            double temp = stringToDouble(fixThrustFractionTF.getText());
            if (Double.isFinite(temp)) {
                 thrustFractionFixedValue = temp;
            }
        }
        else {
            //System.out.println("thrust fraction UNFIXED");
            thrustFractionIsFixed = false;
        }
    }
    

    public void applyFieldValues() {
        //if (activeVehicle == null) {
        //    LOGGER.warning("Tried to apply field values to a null vehicle server!");
        //    return;
        //}
        double temp;

        // Thrust PID
        temp = stringToDouble(thrustPTF.getText());
        if (Double.isFinite(temp)) {
            thrustP = temp;
        }
        temp = stringToDouble(thrustITF.getText());
        if (Double.isFinite(temp)) {
            thrustI = temp;
        }
        temp = stringToDouble(thrustDTF.getText());
        if (Double.isFinite(temp)) {
            thrustD = temp;
        }
        // Rudder PID
        temp = stringToDouble(rudderPTF.getText());
        if (Double.isFinite(temp)) {
            rudderP = temp;
        }
        temp = stringToDouble(rudderITF.getText());
        if (Double.isFinite(temp)) {
            rudderI = temp;
        }
        temp = stringToDouble(rudderDTF.getText());
        if (Double.isFinite(temp)) {
            rudderD = temp;
        }
        
        /*
        // PPI
        temp = stringToDouble(PPI_PosP_TF.getText());
        if (Double.isFinite(temp)) {
            PPI_PosP = temp;
        }
        temp = stringToDouble(PPI_VelP_TF.getText());
        if (Double.isFinite(temp)) {
            PPI_VelP = temp;
        }
        temp = stringToDouble(PPI_VelI_TF.getText());
        if (Double.isFinite(temp)) {
            PPI_VelI = temp;
        } 
        */
        
        // peak motor signals
        temp = stringToDouble(peakForwardMotorSignal_TF.getText());
        if (Double.isFinite(temp)) {
            peakForwardMotorSignal = temp;
        }
        temp = stringToDouble(peakBackwardMotorSignal_TF.getText());
        if (Double.isFinite(temp)) {
            peakBackwardMotorSignal = temp;
        }        
        
        // Winch
        temp = stringToDouble(winchTF.getText());
        if (Double.isFinite(temp)) {
            winch = temp;
        }
        
        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        activeProxy.containers.setThrustPIDGains(thrustP, thrustI, thrustD);
        activeProxy.containers.setBearingPIDGains(rudderP, rudderI, rudderD);
        //activeProxy.containers.setThrustPPIGains(PPI_PosP, PPI_VelP, PPI_VelI);
        activeProxy.containers.setPeakForwardMotorSignal(peakForwardMotorSignal);
        activeProxy.containers.setPeakBackwardMotorSignal(peakBackwardMotorSignal);
        knowledge.sendModifieds();

        /*
        // Always send in case of communication problems
        activeVehicle.setGains(THRUST_GAINS_AXIS, new double[]{thrustP, thrustI, thrustD}, new FunctionObserver<Void>() {
            public void completed(Void v) {
                LOGGER.fine("Set thrust gains succeeded: Axis [" + THRUST_GAINS_AXIS + "] PID [" + thrustP + ", " + thrustI + ", " + thrustD + "]");
            }

            public void failed(FunctionObserver.FunctionError fe) {
                LOGGER.severe("Set thrust gains failed: Axis [" + THRUST_GAINS_AXIS + "] PID [" + thrustP + ", " + thrustI + ", " + thrustD + "]");
            }
        });
        activeVehicle.setGains(RUDDER_GAINS_AXIS, new double[]{rudderP, rudderI, rudderD}, new FunctionObserver<Void>() {
            public void completed(Void v) {
                LOGGER.fine("Set rudder gains succeeded: Axis [" + RUDDER_GAINS_AXIS + "] PID [" + rudderP + ", " + rudderI + ", " + rudderD + "]");
            }

            public void failed(FunctionObserver.FunctionError fe) {
                LOGGER.severe("Set rudder gains failed: Axis [" + RUDDER_GAINS_AXIS + "] PID [" + rudderP + ", " + rudderI + ", " + rudderD + "]");
            }
        });
        activeVehicle.setGains(WINCH_GAINS_AXIS, new double[]{winch, winch, winch}, new FunctionObserver<Void>() {
            public void completed(Void v) {
                LOGGER.fine("Set winch gains succeeded: Axis [" + WINCH_GAINS_AXIS + "] PID [" + winch + ", " + winch + ", " + winch + "]");
            }

            public void failed(FunctionObserver.FunctionError fe) {
                LOGGER.severe("Set winch gains failed: Axis [" + WINCH_GAINS_AXIS + "] PID [" + winch + ", " + winch + ", " + winch + "]");
            }
        });
        */
    }

    public void setProxy(BoatProxy boatProxy) {
        if (activeProxy == boatProxy) {
            return;
        }
        // Stop listening to the old vehicle
        if (activeWinchObserver != null) {
            activeWinchObserver.removeListener(this);
        }

        activeProxy = boatProxy;
        if (activeProxy != null) {
            //activeVehicle = boatProxy.getVehicleServer();
            
            double[] thrustPIDGains = activeProxy.containers.getThrustPIDGains();
            double[] bearingPIDGains = activeProxy.containers.getBearingPIDGains();
            //double[] thrustPPIGains = activeProxy.containers.getThrustPPIGains();
            peakForwardMotorSignal = activeProxy.containers.getPeakForwardMotorSignal();
            peakBackwardMotorSignal = activeProxy.containers.getPeakBackwardMotorSignal();
            thrustPTF.setText(String.format("%.2f", thrustPIDGains[0]));
            thrustITF.setText(String.format("%.2f", thrustPIDGains[1]));
            thrustDTF.setText(String.format("%.2f", thrustPIDGains[2]));
            rudderPTF.setText(String.format("%.2f", bearingPIDGains[0]));
            rudderITF.setText(String.format("%.2f", bearingPIDGains[1]));
            rudderDTF.setText(String.format("%.2f", bearingPIDGains[2]));
            //PPI_PosP_TF.setText(String.format("%.2f", thrustPPIGains[0]));
            //PPI_VelP_TF.setText(String.format("%.2f", thrustPPIGains[1]));
            //PPI_VelI_TF.setText(String.format("%.2f", thrustPPIGains[2]));    
            peakForwardMotorSignal_TF.setText(String.format("%.2f",peakForwardMotorSignal));
            peakBackwardMotorSignal_TF.setText(String.format("%.2f",peakBackwardMotorSignal));
              

            // Retrieve vehicle specific values
            //@todo Ideally we would only do this if the teleop panel is opened

            // Thrust gains
/*          
            activeVehicle.getGains(THRUST_GAINS_AXIS, new FunctionObserver<double[]>() {
                public void completed(double[] values) {
                    LOGGER.fine("Get thrust gains succeeded: Axis [" + THRUST_GAINS_AXIS + "] PID [" + values[0] + ", " + values[1] + ", " + values[2] + "]");
                    thrustPTF.setText("" + values[0]);
                    thrustITF.setText("" + values[1]);
                    thrustDTF.setText("" + values[2]);
                }

                public void failed(FunctionObserver.FunctionError fe) {
                    LOGGER.severe("Get thrust gains failed: Axis [" + THRUST_GAINS_AXIS + "]");
                }
            });
        
            // Rudder gains
            activeVehicle.getGains(RUDDER_GAINS_AXIS, new FunctionObserver<double[]>() {
                public void completed(double[] values) {
                    LOGGER.fine("Get rudder gains succeeded: Axis [" + RUDDER_GAINS_AXIS + "] PID [" + values[0] + ", " + values[1] + ", " + values[2] + "]");
                    rudderPTF.setText("" + values[0]);
                    rudderITF.setText("" + values[1]);
                    rudderDTF.setText("" + values[2]);
                }

                public void failed(FunctionObserver.FunctionError fe) {
                    LOGGER.severe("Get rudder gains failed: Axis [" + RUDDER_GAINS_AXIS + "]");
                }
            });
            // Winch
            activeVehicle.getGains(WINCH_GAINS_AXIS, new FunctionObserver<double[]>() {
                public void completed(double[] values) {
                    LOGGER.fine("Get winch gains succeeded: Axis [" + WINCH_GAINS_AXIS + "] Value [" + values[0] + "]");
                    winchTF.setText("" + values[0]);
                }

                public void failed(FunctionObserver.FunctionError fe) {
                    LOGGER.severe("Get winch gains failed: Axis [" + WINCH_GAINS_AXIS + "]");
                }
            });
            winchL.setText("Winch value: ---");
            activeWinchObserver = Engine.getInstance().getObserverServer().getObserver(activeProxy, WINCH_GAINS_AXIS);
            activeWinchObserver.addListener(this);

            applyB.setEnabled(true);
        */
        } 
        
        else {
            //activeVehicle = null;
            // No vehicle selected, blank out text fields
            thrustPTF.setText("");
            thrustITF.setText("");
            thrustDTF.setText("");
            rudderPTF.setText("");
            rudderITF.setText("");
            rudderDTF.setText("");
            winchTF.setText("");
            peakForwardMotorSignal_TF.setText("");
            peakBackwardMotorSignal_TF.setText("");
            applyB.setEnabled(false);
        }
    }

    @Override
    public void eventOccurred(sami.event.InputEvent ie) {
    }

    @Override
    public void newObservation(Observation o) {
        if (!o.getSource().equals(activeProxy.getName())) {
            LOGGER.warning("Received observation from proxy other than active proxy!");
            return;
        } else {
            winchL.setText("Winch value: " + decimalFormat.format(o.getValue()));
        }
    }
}
