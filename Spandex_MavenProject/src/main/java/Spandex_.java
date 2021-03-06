/* 
 * Single Particle Analysis and Detection EXperience
 * (SPANDEX)
 * for Single Particle IRIS (SP-IRIS)
 * 
 * @author Derin Sevenler <derin@bu.edu>
 * Created February 2017
 */

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import edu.emory.mathcs.restoretools.Enums;
import edu.emory.mathcs.restoretools.iterative.IterativeEnums;
import edu.emory.mathcs.restoretools.iterative.mrnsd.MRNSDDoubleIterativeDeconvolver2D;
import edu.emory.mathcs.restoretools.iterative.mrnsd.MRNSDOptions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Undo;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.RankFilters;
import inra.ijpb.watershed.*;
import inra.ijpb.morphology.*;

@SuppressWarnings("restriction")

public class Spandex_ implements PlugIn {
	protected ImagePlus image;
	// image property members
	private double sigma;
	private boolean showIntermediateImages;
	private int imWidth;
	private int imHeight;
	private int zSize;
	private int threshrad,conthresh;
	private ImagePlus rawImgPlus;
	private ImagePlus nirImagePlus;
	private ImagePlus psf,dispim;

	private FloatProcessor maxVals;
	private FloatProcessor minVals;
	private FloatProcessor maxIdx;
	private FloatProcessor minIdx;
	private FloatProcessor nirs;
	private FloatProcessor pps;

	private double[] xPos;
	private double[] yPos;
	private List<Double> xPosFiltered;
	private List<Double> yPosFiltered;
	private boolean isParticle=true;

	private IterativeEnums.PreconditionerType preconditioner;
	private double preconditionerTol;
	private IterativeEnums.BoundaryType boundary;
	private IterativeEnums.ResizingType resizing;
	private Enums.OutputType output;
	private int maxIters;
	private boolean showIteration;
	private MRNSDOptions options;
	
	private ImagePlus[][] psfa;
	private String psfp;
	private boolean tx,twx,fx,ctbool,radbool;
	private int cctthresh,cthreshrad;
	
	public void run(String arg) {
		if (showDialog()) {
			rawImgPlus = IJ.getImage();
			psfp = IJ.getFilePath("Choose PSF File:");
			psf = IJ.openImage(psfp);
			psfa = new ImagePlus[1][1];
			psfa[0][0]=psf;
			imWidth = rawImgPlus.getWidth();
			imHeight = rawImgPlus.getHeight();
			zSize = rawImgPlus.getNSlices();
			nirImagePlus = performPreProcessing();
			dispim=findKeyPoints(nirImagePlus.getStack());
			if(isParticle){
			filterKeyPoints();
			displayResults();
			}
			
		}
		

	}
	
	private ImagePlus performPreProcessing(){
		// Perform filtering and smoothing on the stack to reduce shot noise and illumination gradients.
		// We are basically making the image into NI by subtracting and then dividing by E_ref
		
		// medianImage is essentially our estimate for E_ref
		ImagePlus medianImage = rawImgPlus.duplicate();
		medianImage.setTitle("Background Image");
		// Uses the 'Fast Filters' plugin
		int kernelSize = (int)(Math.round(20*sigma));
		IJ.run(medianImage, "Fast Filters", "link filter=median x=" + kernelSize + " y=" + kernelSize + " preprocessing=none stack");
		
		// Subtract medianImage from original
		ImageCalculator ic = new ImageCalculator();
		ImagePlus diffImage = ic.run("Subtract create 32-bit stack", rawImgPlus, medianImage);
		diffImage.setTitle("Difference image");
		
		// Divide by the medianImage to get niImg
		// Convert from 16-bit unsigned int to float
		ImagePlus niImg = ic.run("Divide create 32-bit stack", diffImage, medianImage);
		niImg.setTitle("Normalized intensity image");


		// Perform smoothing.
		// Convolution with the correct kernel does not effect peak amplitude but reduces noise
		IJ.run(niImg, "Gaussian Blur 3D...", "x=" + sigma + " y=" + sigma + " z=1");
		
		return niImg;
	}

	private ImagePlus findKeyPoints(ImageStack imageStack){
		// Find the min and max values and indices


		maxVals = (FloatProcessor) imageStack.getProcessor(1).duplicate();
		float[] maxValPixs = (float[]) maxVals.getPixels();
		minVals = (FloatProcessor) imageStack.getProcessor(1).duplicate();
		float[] minValPixs = (float[]) minVals.getPixels();
		maxIdx = new FloatProcessor(imWidth, imHeight);
		float[] maxIdxPixs = (float[]) maxIdx.getPixels();
		minIdx = new FloatProcessor(imWidth, imHeight);
		float[] minIdxPixs = (float[]) minIdx.getPixels();
		for (int idz = 1; idz<zSize; idz++){
			ImageProcessor thisSlice = imageStack.getProcessor(idz+1);
			float[] thisSlicePixs = (float[]) thisSlice.getPixels();
			for (int idx = 0; idx<thisSlicePixs.length; idx++){
				if ((thisSlicePixs[idx]) > (maxValPixs[idx])){
					maxValPixs[idx] = thisSlicePixs[idx];
					maxIdxPixs[idx] = idz;
				} else if ((thisSlicePixs[idx]) < (minValPixs[idx])){
					minValPixs[idx] = thisSlicePixs[idx];
					minIdxPixs[idx] = idz;
				}
			}
			IJ.showProgress((idz+1),zSize);
		}

		// Calculate the NIR and PPS
		nirs = new FloatProcessor(imWidth, imHeight);
		pps = new FloatProcessor(imWidth, imHeight);
		float[] nirPixs = (float[]) nirs.getPixels();		
		for (int idx = 0; idx<maxValPixs.length; idx++){
			nirPixs[idx] = ((float)(maxValPixs[idx]) - (float)(minValPixs[idx]));
		}
		ImagePlus nirPlus = new ImagePlus("Normalized Intensity Range", nirs);
		if (showIntermediateImages){
			ImagePlus nirShow = nirPlus.duplicate();
			nirShow.show();
		}
		
		IJ.run(nirPlus, "Gaussian Blur...", "radius=1.5");
		
		//init options for deconv and do deconv
		boolean autoStoppingTol = true;
		boolean logConvergence = false;
		double stoppingTol = 0;
		double threshold = 0;
		boolean useThreshold = false;
		options  = new MRNSDOptions(autoStoppingTol,stoppingTol,useThreshold,threshold,logConvergence);

		new MRNSDDoubleIterativeDeconvolver2D(nirPlus, psfa, preconditioner, preconditionerTol, 
				boundary, resizing, output, maxIters, showIteration, options);

		// take original
		ImagePlus nirpro = nirPlus.duplicate();
		ImagePlus nirdisp = nirPlus.duplicate();
		IJ.run(nirpro,"Invert","");

		//take inverted
		ImageProcessor nirprop = nirpro.getProcessor();
		
		// convert to 8 bit and apply Local Adaptive Threshold with Bernsen method
		ImageConverter imconv = new ImageConverter(nirPlus);
		imconv.convertToGray8();
		ImagePlus nirPlus1 = Bernsen(nirPlus,threshrad,conthresh,0,true);
		ImagePlus showex = nirPlus1.duplicate();
		if(showIntermediateImages){
		showex.show();
		}
		ImageProcessor nirbip = nirPlus1.getProcessor();
		
		// do watershed
		WatershedTransform2D wshed = new WatershedTransform2D(nirprop,nirbip);
		ImageProcessor wshedim = wshed.apply();
		ImagePlus wshedimp = new ImagePlus("watershed",wshedim);
		if(showIntermediateImages){
		wshedimp.show();
		}
		
		/// apply watershed
		double maxval = wshedim.getMax();
		IJ.setThreshold(wshedimp, .99, maxval);
		IJ.run(wshedimp,"Make Binary","");
		ImageProcessor finim = wshedimp.getProcessor();
		
		IJ.selectWindow("Log");
		IJ.run("Close");
		//init result of morph ops
		ImagePlus check = null;
		
		//morphological operations on basis of magnification
		
		Strel strel = Strel.Shape.DISK.fromRadius(1);
		
		if(tx){
			check = new ImagePlus("Particles",finim);}	
		if(twx || fx){
			ImageProcessor finim1 = Morphology.erosion(finim,strel);
			check = new ImagePlus("Particles",finim1);}
		if(showIntermediateImages){
			check.show();
		}
		
		//detect particles
		IJ.run(check,"Analyze Particles...", "size=0-400 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");

		ResultsTable resultsTable = ResultsTable.getResultsTable();
		

		int xCol = resultsTable.getColumnIndex("XStart");
		if(xCol==resultsTable.COLUMN_NOT_FOUND){
			isParticle=false;
			IJ.error("No particle is found");
			return nirdisp;
		}

		int yCol =  resultsTable.getColumnIndex("YStart");

		xPos = resultsTable.getColumnAsDoubles(xCol);
		yPos = resultsTable.getColumnAsDoubles(yCol);
		
		if (!showIntermediateImages){
			IJ.selectWindow("Results");
			IJ.run("Close");
			IJ.selectWindow("ROI Manager");
			IJ.run("Close");
		}
		return nirdisp;
	}

	private void filterKeyPoints(){
		// delete any keypoints within the bare Si region
		xPosFiltered = new ArrayList<Double>();
		yPosFiltered = new ArrayList<Double>();
		for (int n = 0; n<xPos.length; n++){
			int thisXpx = (int)Math.round(xPos[n]);
			int thisYpx = (int)Math.round(yPos[n]);
				xPosFiltered.add(xPos[n]);
				yPosFiltered.add(yPos[n]);
		}
	}

	private void displayResults(){
		int nParticles = xPosFiltered.size();
		// Create an overlay to show particles
		Overlay particleOverlay = new Overlay();
		for (int n = 0; n<nParticles; n++){
			Roi thisParticle = new OvalRoi(xPosFiltered.get(n)-4, yPosFiltered.get(n)-4, 16, 16);
			thisParticle.setStrokeColor(Color.red);
			particleOverlay.add(thisParticle);
		}
		dispim.show();
		dispim.setOverlay(particleOverlay);
		//IJ.run(rawImgPlus,"Enhance Contrast", "saturated=0.4");

		// create a resultsTable and put it in the resultsWindow
		ResultsTable resultsTable = new ResultsTable();
		for (int n = 0; n<nParticles; n++){
			resultsTable.setValue("x", n, xPosFiltered.get(n));
			resultsTable.setValue("y", n, yPosFiltered.get(n));
		}
		resultsTable.show("Particle Results");
	}

	private boolean showDialog() {

		preconditioner = IterativeEnums.PreconditionerType.valueOf("FFT");
		preconditionerTol = -1;
		boundary = IterativeEnums.BoundaryType.valueOf("REFLEXIVE");
		resizing = IterativeEnums.ResizingType.valueOf("AUTO");
		output = Enums.OutputType.valueOf("SAME_AS_SOURCE");
		maxIters = 9;
		showIteration = true;
		
		GenericDialog gd = new GenericDialog("WELCOME TO SPANDEX");
		
		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("Sigma: decrease for small particles", 1.5, 1);
		gd.addNumericField("Contrast Threshold (8-9 is default) - Decrease for dim particles", 0, 1);
		gd.addNumericField("Thresholding Radius (14-19 is default) - Decrease for small particles", 0, 1);
		gd.addCheckbox("Show intermediate images", false);
		gd.addCheckbox("10x", true);
		gd.addCheckbox("20x", false);
		gd.addCheckbox("50x", false);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		sigma = gd.getNextNumber();
		cctthresh = (int) gd.getNextNumber();
		cthreshrad = (int) gd.getNextNumber();
		showIntermediateImages = gd.getNextBoolean();
		tx = gd.getNextBoolean();
		twx = gd.getNextBoolean();
		fx = gd.getNextBoolean();
		
		//set radius and contrast threshold for binarization
		if(tx){
				threshrad=14;
				conthresh=8;
			}else if(twx){
				threshrad=15;
				conthresh=9;
			}else if(fx){
				threshrad=19;
				conthresh=9;
		}
		
		if(!(cthreshrad==0)){
			threshrad=cthreshrad;
		}
		if(!(cctthresh==0)){
			conthresh=cctthresh;
		}

		if(((tx^twx)^fx)){ return true; } else { IJ.error("Select one magnification!"); return false;}

	}

	public void showAbout() {
		IJ.showMessage("SPANDEX: the Single Particle Analysis and Detection EXperience",
			" developed at Boston University for Single Particle Interferometric Reflectance Imaging Sensing (SP-IRIS)"
		);
	}
	
	private ImagePlus Bernsen(ImagePlus imp, int radius,  double par1, double par2, boolean doIwhite ) {
        //adaptive local thresholding method taken from IJ's adaptive thresholding plugin to make setting options easier
		ImagePlus Maximp, Minimp;
		ImageProcessor ip=imp.getProcessor(), ipMax, ipMin;
		int contrast_threshold=15;
		int local_contrast;
		int mid_gray;
		byte object;
		byte backg;
		int temp;
		// 0 - Check validity of parameters
		if (null == imp) return null;
		int xe = ip.getWidth();
		int ye = ip.getHeight();

		IJ.showStatus("Thresholding...");
		long startTime = System.currentTimeMillis();
		//1 Do it
		if (imp.getStackSize()==1){
			    ip.snapshot();
			    Undo.setup(Undo.FILTER, imp);
		}
		if (par1!=0) {
			IJ.log("Bernsen: changed contrast_threshold from :"+ contrast_threshold + "  to:" + par1);
			contrast_threshold= (int) par1;
		}

		if (doIwhite){
			object =  (byte) 0xff;
			backg =   (byte) 0;
		}
		else {
			object =  (byte) 0;
			backg =  (byte) 0xff;
		}
		ImagePlus implus = new ImagePlus("",ip);
		Maximp=implus.duplicate();
		ipMax=Maximp.getProcessor();
		RankFilters rf=new RankFilters();
		rf.rank(ipMax, radius, rf.MAX);// Maximum
		//Maximp.show();
		ImagePlus implus1 = new ImagePlus("",ip);
		Minimp=implus1.duplicate();
		ipMin=Minimp.getProcessor();
		rf.rank(ipMin, radius, rf.MIN); //Minimum
		//Minimp.show();
		byte[] pixels = (byte [])ip.getPixels();
		byte[] max = (byte [])ipMax.getPixels();
		byte[] min = (byte [])ipMin.getPixels();

		for (int i=0; i<pixels.length; i++) {
			local_contrast = (int)((max[i]&0xff) -(min[i]&0xff));
			mid_gray =(int) ((min[i]&0xff) + (max[i]&0xff) )/ 2;
			temp=(int) (pixels[i] & 0x0000ff);
			if ( local_contrast < contrast_threshold )
				pixels[i] = ( mid_gray >= 128 ) ? object :  backg;  //Low contrast region
			else
				pixels[i] = (temp >= mid_gray ) ? object : backg;
		}    
		//imp.updateAndDraw();
		return imp;
	}


}
