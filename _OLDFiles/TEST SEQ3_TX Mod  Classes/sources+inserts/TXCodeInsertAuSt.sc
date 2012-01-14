// Copyright (C) 2009  Paul Miller. This file is part of TX Modular system distributed under the terms of the GNU General Public License (see file LICENSE).

TXCodeInsertAuSt : TXModuleBase {

	classvar <>arrInstances;	
	classvar <defaultName;  		// default module name
	classvar <moduleRate;			// "audio" or "control"
	classvar <moduleType;			// "source", "insert", "bus",or  "channel"
	classvar <noInChannels;			// no of input channels 
	classvar <arrAudSCInBusSpecs; 	// audio side-chain input bus specs 
	classvar <>arrCtlSCInBusSpecs; 	// control side-chain input bus specs
	classvar <noOutChannels;		// no of output channels 
	classvar <arrOutBusSpecs; 		// output bus specs
	classvar	<guiHeight=100;
	classvar	<guiWidth=450;
	classvar	<guiLeft=100;
	classvar	<guiTop=300;
	
	var userFunctionString;

*initClass{
	arrInstances = [];		
	//	set class specific variables
	defaultName = "Code Insert A St";
	moduleRate = "audio";
	moduleType = "insert";
	noInChannels = 2;			
	arrCtlSCInBusSpecs = [ 
		["Modify 1", 1, "modChange1", 0],
		["Modify 2", 1, "modChange2", 0],
		["Modify 3", 1, "modChange3", 0],
		["Modify 4", 1, "modChange4", 0],
	];	
	noOutChannels = 2;
	arrOutBusSpecs = [ 
		["Out L + R", [0,1]], 
		["Out L only", [0]], 
		["Out R only", [1]] 
	];	
} 

*new{ arg argInstName;
	 ^super.new.init(argInstName);
} 

init {arg argInstName;
	//	set  class specific instance variables
	userFunctionString = "// Example code
{arg inSignalL, inSignalR, mod1, mod2, mod3, mod4; 
  [inSignalL,inSignalR] * 
  [Pulse.ar(SinOsc.ar(0.5, 0, 60,60)
    + [60,400].asSpec.map(mod1), 0.1, 0.3), 
  SinOsc.ar(SinOsc.ar(0.3333, 0, 60,60)
    + [60,400].asSpec.map(mod2), 0, 0.3)]; }";
	arrSynthArgSpecs = [
		["in", 0, 0],
		["out", 0, 0],
		["change1", 0, defLagTime],
		["change1Min", 0, defLagTime],
		["change1Max", 1, defLagTime],
		["change2", 0, defLagTime],
		["change2Min", 0, defLagTime],
		["change2Max", 1, defLagTime],
		["change3", 0, defLagTime],
		["change3Min", 0, defLagTime],
		["change3Max", 1, defLagTime],
		["change4", 0, defLagTime],
		["change4Min", 0, defLagTime],
		["change4Max", 1, defLagTime],
		["modChange1", 0, defLagTime],
		["modChange2", 0, defLagTime],
		["modChange3", 0, defLagTime],
		["modChange4", 0, defLagTime],
	]; 
	synthDefFunc = { 
		arg in, out, change1, change1Min, change1Max, change2, change2Min, change2Max,
			change3, change3Min, change3Max, change4, change4Min, change4Max, 
			modChange1 = 0, modChange2 = 0, modChange3 = 0, modChange4 = 0;
		var inSignalL, inSignalR, outChange1, outChange2, outChange3, outChange4;
		inSignalL = InFeedback.ar(in,1);
		inSignalR = InFeedback.ar(in+1,1);
		outChange1 = change1Min + ((change1Max - change1Min) * (change1 + modChange1).max(0).min(1));
		outChange2 = change2Min + ((change2Max - change2Min) * (change2 + modChange2).max(0).min(1));
		outChange3 = change3Min + ((change3Max - change3Min) * (change3 + modChange3).max(0).min(1));
		outChange4 = change4Min + ((change4Max - change4Min) * (change4 + modChange4).max(0).min(1));
//		// use TXClean to stop blowups
//		Out.ar(out, TXClean.ar(
//			userFunctionString.compile.value.value(outChange1, outChange2, outChange3, outChange4)
//		));
		Out.ar(out, 
			userFunctionString.compile.value.value(inSignalL, inSignalR, outChange1, outChange2, outChange3, outChange4)
		);
	};
	guiSpecArray = [
		["TXMinMaxSliderSplit", "Modify 1", \unipolar, "change1", "change1Min", "change1Max"], 
		["TXMinMaxSliderSplit", "Modify 2", \unipolar, "change2", "change2Min", "change2Max"], 
		["TXMinMaxSliderSplit", "Modify 3", \unipolar, "change3", "change3Min", "change3Max"], 
		["TXMinMaxSliderSplit", "Modify 4", \unipolar, "change4", "change4Min", "change4Max"], 
		["TextViewDisplay", "Coding Notes: Enter Supercollider 3 code in the window below. The code needs to be a function which returns a stereo audio signal. The function will be passed the arguments: InSignalL, InSignalR, Modify 1, Modify 2, Modify 3, Modify 4.
Use the Evaulate text button to evaulate the code.", 400, 70, "Notes"],
		["TextViewCompile", userFunctionString, {arg argText; this.evaluate(argText);}, 400, 200],
	];
	arrActionSpecs = this.buildActionSpecs(guiSpecArray);
	//	use base class initialise 
	this.baseInit(this, argInstName);
	//	load the synthdef and create the synth
	this.loadAndMakeSynth;
}

evaluate {arg argText; 
	var compileResult;
	compileResult = argText.compile;
	if (compileResult.isNil, {
		TXInfoScreen.new("ERROR: code will not compile - see post window ");
	},{
		userFunctionString = argText;
	});
	this.rebuildSynth;
}

extraSaveData { // override default method
	^[userFunctionString];
}

loadExtraData {arg argData;  // override default method
	userFunctionString = argData.at(0);
}

}

