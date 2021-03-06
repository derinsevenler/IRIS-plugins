import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.math3.analysis.function.Exp;
import org.apache.commons.math3.complex.Complex;

public class generate_LUT implements PlugIn {
	public ImagePlus imp;
	public boolean canContinue,didCancel;
	public int s1,s2;
	public double[] filmavgs = new double[4],refavgs = new double[4];
	public double[][] c1 = new double[2501][2],c2 = new double[2501][2],c3 = new double[2501][2],c4 = new double[2501][2];
	public double[][] risi = new double[129][2],risio2 = new double[101][2],ripmma = new double[101][2];
    public ArrayList<Float> univ = new ArrayList<Float>();
	public JFrame roiguide;
	public JLabel refLabel,filmLabel;
	
	
	public void run(String arg){
		imp = IJ.getImage();
		
        
        /*
        if(!(imp.getNChannels()==4)||!(imp.getNFrames()==1)) IJ.error("Use 4 channel, single frame image!");
        getRef();
        */
	}	
	
	public double fresnel(double n1,double n2,double n3,double d,double l){
		double r0,r1,D,req=0;
		r0=(n1-n2)/(n1+n2);
		r1=(n2-n3)/(n2+n3);
		D=((Math.PI)*2*d*n2)/l;
		Complex mid,id;
		id= new Complex(0.0,D);
		mid= new Complex(0.0,D);
		mid=mid.multiply(-1);
		Complex meid=mid.exp();
		Complex eid=id.exp();
		Complex numer=((meid.multiply(r0)).add((eid.multiply(r1))));
		Complex denom=meid.add(eid.multiply(r0*r1));
		req=(numer.divide(denom)).abs();
		return req;
	}

	public void getRef(){
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(1,2,0,0));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);
		
		JButton referenceButton = new JButton("Get reference intensity");
		referenceButton.setEnabled(true);
		referenceButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					refavgs[i-1]=takeroimean(imp.getRoi());
				}
			}
		});
		selectPanel.add(referenceButton);
		
		JButton filmButton = new JButton("Get film intensities");
		filmButton.setEnabled(true);
		filmButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					filmavgs[i-1]=takeroimean(imp.getRoi());
				}
			}
		});
		

		
		filmLabel = new JLabel("");
		selectPanel.add(filmButton);
		selectPanel.add(filmLabel);
		
		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide.dispose();
			}
		});
		buttonPanel.add(cancelButton);
		
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
					canContinue=true;
					roiguide.dispose();	
			}
		});
		buttonPanel.add(okButton);

		// Create and populate the JFrame
		roiguide = new JFrame("Get film and reference reflectance values:");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(400,400);
		roiguide.setVisible(true);
		roiguide.setResizable(false);
		roiguide.pack();
		
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
	
	public double takeroimean(Roi roi) {
		ImagePlus imp = IJ.getImage();
		if (roi!=null && !roi.isArea()) roi = null;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor mask = roi!=null?roi.getMask():null;
		Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
		double sum = 0;
		int count = 0;
		for(int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask==null||mask.getPixel(x,y)!=0) {
					count++;
					sum += ip.getPixelValue(x+r.x, y+r.y);
				}
			}
		}
		return (sum/count);
		}

public void loaddata(){
	TextWindow tw = new TextWindow("C:/Users/Mike/Desktop/OCN/ledspectra.txt",1,1);
    TextPanel tp = tw.getTextPanel();
    
    for(int i=1;i<=tp.getLineCount()-1;i++){
    	String s[] = tp.getLine(i).split("	");
    	c1[i][0]=(Float.parseFloat(s[0]));
    	c1[i][1]=(Float.parseFloat(s[1]));
    	c2[i][0]=(Float.parseFloat(s[2]));
    	c2[i][1]=(Float.parseFloat(s[3]));
    	c3[i][0]=(Float.parseFloat(s[4]));
    	c3[i][1]=(Float.parseFloat(s[5]));
    	c4[i][0]=(Float.parseFloat(s[6]));
    	c4[i][1]=(Float.parseFloat(s[7]));
    	s1=i;
    }
    tw.dispose();
    tw = new TextWindow("C:/Users/Mike/Desktop/OCN/test1.txt",1,1);
    tp = tw.getTextPanel();
    
    for(int i=1;i<=tp.getLineCount()-1;i++){
    	String s[] = tp.getLine(i).split("	");
    	risi[i][0]=(Float.parseFloat(s[0]));
    	risi[i][1]=(Float.parseFloat(s[1]));
    	if(i<=100){
	    	risio2[i][0]=(Float.parseFloat(s[2]));
	    	risio2[i][1]=(Float.parseFloat(s[3]));
	    	ripmma[i][0]=(Float.parseFloat(s[4]));
	    	ripmma[i][1]=(Float.parseFloat(s[5]));
    	}
    	s2=i;
    }
}
//figure out how matlab interpolates and then go from there
public float interpolatespectra(float data[][],float input,int j,int k){
	boolean oobret;
	int ind=-1;
	for(int i=0;i<s1;i++){
		if(input>r.get(i) && input<r.get(i+1)) ind=i;
	}
	float x1 = r.get(ind);
	float x2 = r.get(ind+1);
	float y1 = h.get(ind);
	float y2 = h.get(ind+1);
	float result=(((y2-y1)/(x2-x1))*(input-x1))+y1;
	if(oobret){
		oobret=false;
		return 0;
	}else {
		return result;
	}
}
public float interpolaterind(float data[][],float input,int j,int k){
	boolean oobret;
	for(int i=0;i<s2;i++){
		if(input>r.get(i) && input<r.get(i+1)) ind=i;
		if((i==r.size()-2)&&(r.get(i+1)<input)) oobret=true;
	}
	float x1 = r.get(ind);
	float x2 = r.get(ind+1);
	float y1 = h.get(ind);
	float y2 = h.get(ind+1);
	float result=(((y2-y1)/(x2-x1))*(input-x1))+y1;
	if(oobret){
		oobret=false;
		return 0;
	}else {
		return result;
	}
}
}
