// Copyright (C) 2010  Paul Miller. This file is part of TX Modular system distributed under the terms of the GNU General Public License (see file LICENSE).

TXConvolution : TXModuleBase {		

	classvar <>arrInstances;	
	classvar <defaultName;  		// default module name
	classvar <moduleRate;			// "audio" or "control"
	classvar <moduleType;			// "source", "insert", "bus",or  "channel"
	classvar <noInChannels;			// no of input channels 
	classvar <arrAudSCInBusSpecs; 	// audio side-chain input bus specs 
	classvar <>arrCtlSCInBusSpecs; 	// control side-chain input bus specs
	classvar <noOutChannels;		// no of output channels 
	classvar <arrOutBusSpecs; 		// output bus specs
	classvar	<guiWidth=500;
	classvar	<arrBufferSpecs;

	var <>sampleNo = 0;
	var <>bankNo = 0;
	var <>sampleData;
	var sampleFileName = "";
	var showWaveform = 0;
	var sampleNumChannels = 0;
	var fftsize = 2048;	// could also use 4096
	
*initClass{
	arrInstances = [];		
	//	set class specific variables
	defaultName = "Convolution";
	moduleRate = "audio";
	moduleType = "insert";
	noInChannels = 1;			
	arrCtlSCInBusSpecs = [ 
		["Input Level", 1, "modInputLevel", 0],
		["Dry-Wet Mix", 1, "modWetDryMix", 0]
	];	
	noOutChannels = 1;
	arrOutBusSpecs = [ 
		["Out", [0]]
	];
	arrBufferSpecs = [ 
		["bufnumSample", 2048, 1], 
		["bufnumSpectrum", 2048, 1],  
		["bufnumDelay", 2*defSampleRate, 1],  

	];
} 

*new{ arg argInstName;
	 ^super.new.init(argInstName);
} 

init {arg argInstName;
	//	set  class specific instance variables
	extraLatency = 0.2;	// allow extra time when recreating
	arrSynthArgSpecs = [
		["in", 0, 0],
		["out", 0, 0],
		["bufnumSample", 0, \ir],
		["bufnumSpectrum", 0, \ir],
		["bufnumDelay", 0, \ir],
		["bankNo", 0, 0],
		["sampleNo", 0, 0],
		["inputLevel", 0.5, defLagTime],
		["predelay", 0, defLagTime],
		["wetDryMix", 1.0, defLagTime],
		["modInputLevel", 0, defLagTime],
		["modWetDryMix", 0, defLagTime],
	]; 
	synthDefFunc = { 
		arg in, out, bufnumSample, bufnumSpectrum, bufnumDelay, bankNo, sampleNo, inputLevel, 
			predelay, wetDryMix, modInputLevel=0, modWetDryMix=0;
		var input, outSound, inputLevelCombined, mixCombined;
		inputLevelCombined = (inputLevel + modInputLevel).max(0).min(1);
		input = TXClean.ar(TXEnvPresets.startEnvFunc.value * InFeedback.ar(in,1));
		mixCombined = (wetDryMix + modWetDryMix).max(0).min(1);
//		outSound = PartConv.ar(input, fftsize, bufnumSpectrum, mixCombined, 
//			input * (1-mixCombined));
		outSound = PartConv.ar(BufDelayC.ar(bufnumDelay, input * inputLevelCombined, predelay), fftsize, 
			bufnumSpectrum, mixCombined, input * (1-mixCombined));
		Out.ar(out, TXClean.ar(outSound));
	};
	this.buildGuiSpecArray;
	arrActionSpecs = this.buildActionSpecs(guiSpecArray);
	//	use base class initialise 
	this.baseInit(this, argInstName);
	//	make buffers, load the synthdef and create the synth
	this.makeBuffersAndSynth(arrBufferSpecs);
}

buildGuiSpecArray {
	guiSpecArray = [
		["TXPopupActionPlusMinus", "Sample bank", {system.arrSampleBankNames},
			"bankNo", 
			{ arg view; this.bankNo = view.value; this.sampleNo = 0; 
				this.makeBuffersAndSynth(arrBufferSpecs); this.loadSample(0); 
				this.setSynthArgSpec("sampleNo", 0); system.showView;}
		], 
		// array of sample filenames - beginning with blank sample  - only show mono files
		["TXPopupActionPlusMinus", "Mono sample", 
			{["No Sample"]++system.sampleMonoFileNames(bankNo, true)},
				"sampleNo", { arg view; this.sampleNo = view.value; 
				this.makeBuffersAndSynth(arrBufferSpecs); this.loadSample(view.value); 
				{system.showView;}.defer(0.2);}
		], 
		["Spacer", 80], 
		["ActionButton", "Add Samples to Sample Bank", 
			{TXBankBuilder2.addSampleDialog("Sample", bankNo)}, 200], 
		["ActionButton", "Show", {showWaveform = 1; system.showView;}, 
			80, TXColor.white, TXColor.sysGuiCol2], 
		["ActionButton", "Hide", {showWaveform = 0; system.showView; this.sampleData_(nil);}, 
			80, TXColor.white, TXColor.sysDeleteCol], 
		["NextLine"], 
		["TXSoundFileView", {sampleFileName}, {this.sampleData}, 
			{arg argData; this.sampleData_(argData);}, nil, {showWaveform}], 
		["SpacerLine", 6], 
		["EZslider", "Input level", ControlSpec(0, 1, \amp), "inputLevel"], 
		["SpacerLine", 6], 
		["EZslider", "Pre-delay", ControlSpec(0.001, 1, \amp), "predelay"],
		["SpacerLine", 6], 
		["WetDryMixSlider"], 
	];
}

extraSaveData { // override default method
	^[sampleNo, sampleFileName, sampleNumChannels, bankNo];
}

loadExtraData {arg argData;  // override default method
	sampleNo = argData.at(0);
	sampleFileName = argData.at(1);
	// Convert path
	sampleFileName = TXPath.convert(sampleFileName);
	sampleNumChannels = argData.at(2);
	bankNo = argData.at(3);
	this.buildGuiSpecArray;
	system.showViewIfModDisplay(this);
	{	this.makeBuffersAndSynth(arrBufferSpecs);
		this.loadSample(sampleNo);
	}.defer(0.2);
}

loadSample { arg argIndex; // method to load samples into buffer
	var holdBuffer, holdSpectrumBufSize, holdSpectrumBuf, holdSampleInd, 
		holdModCondition, holdPath, maxSampleTime;

		Routine.run {
		// max is 10 secs
		maxSampleTime = 10;
		// add condition to load queue
		holdModCondition = system.holdLoadQueue.addCondition;
		// pause
		holdModCondition.wait;
		// pause
		system.server.sync;
		// adjust index
		holdSampleInd = (argIndex - 1).min(system.sampleFilesMono(bankNo).size-1);
		// check for invalid samples
		if (argIndex == 0 or: {system.sampleFilesMono(bankNo).at(holdSampleInd).at(3) == false}, {
			// if argIndex is 0, clear the current buffer & filename
			holdBuffer = Buffer.alloc(system.server, 2048, 1, 
				bufnum: buffers.at(0).bufnum);
			sampleFileName = "";
		},{
			// otherwise,  try to load sample.  if it fails, display error message and clear
			holdPath = system.sampleFilesMono(bankNo).at(holdSampleInd).at(0);
			// Convert path
			holdPath = TXPath.convert(holdPath);
			holdBuffer = Buffer.read(system.server, holdPath, 
				numFrames: maxSampleTime * system.server.sampleRate, 
				action: { arg argBuffer; 
					{ 
					//	if file loaded ok
						if (argBuffer.notNil, {
							this.setSynthArgSpec("bufnumSample", argBuffer.bufnum);
							sampleFileName = system.sampleFilesMono(bankNo).at(holdSampleInd).at(0);
							sampleNumChannels = argBuffer.numChannels;
						},{
							buffers.at(0).zero;
							sampleFileName = "";
							sampleNumChannels = 0;
							TXInfoScreen.new("Invalid Sample File" 
							  ++ system.sampleFilesMono(bankNo).at(holdSampleInd).at(0));
						});
					}.defer;	// defer because gui process
				},
				// pass buffer number
				bufnum: buffers.at(0).bufnum
			);
		});

		// pause
		system.server.sync;
		holdSpectrumBufSize= PartConv.calcBufSize(fftsize, holdBuffer);
		holdSpectrumBuf= Buffer.alloc(system.server, holdSpectrumBufSize, 1, 
			bufnum: buffers.at(1).bufnum);
		holdSpectrumBuf.preparePartConv(holdBuffer, fftsize);
		// pause
		system.server.sync;
		// remove condition from load queue
		system.holdLoadQueue.removeCondition(holdModCondition);
	}; // end of Routine.run
} // end of method loadSample


}

