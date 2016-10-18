package rocks.fretx.notedetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.MidiTrack;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOff;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.event.meta.Tempo;
import com.pdrogfer.mididroid.event.meta.TimeSignature;
import com.pdrogfer.mididroid.util.MetronomeTick;
import com.pdrogfer.mididroid.util.MidiProcessor;


import java.util.ArrayList;
import java.util.Iterator;


public class MainActivity extends AppCompatActivity {

    private final int PERMISSION_CODE_RECORD_AUDIO = 42; //This is arbitrary, so why not The Answer to Life, Universe, and Everything?
    AudioInputHandler audioInputHandler;
    private Thread audioThread;
    private Thread guiThread;
    private boolean processingIsRunning = false;
    protected PitchDetectorYin yin;
    private boolean practiceMode = true;

    MidiFile midi;
    MidiProcessor processor;
    EventDisplayer eventDisplayer;
    float [] originalBpms;
    int currentMidiNote;
    int totalNotes = 0;
    int correctNotes = 0;
    int mistakes = 0;

    TextView eventText;
    TextView correctText;
    TextView mistakeText;
    TextView metronomeText;
    RadioButton practiceRadio;
    RadioButton playRadio;
    RadioGroup modeRadioGroup;
    SeekBar tempoSeek;
    TextView tempoText;
    FretboardView fretboard;

    CountDownTimer noteTimer;
    CountDownTimer metronomeTimer;
    long timeLeftForCorrectNote = -1;
    boolean correctNoteLock;
    boolean mistakeLock = true;

    Iterator<MidiEvent> noteIterator;

    private final double ERROR_THRESHOLD_IN_SEMITONES = 0.5;
    private final long CORRECT_NOTE_COUNTDOWN = 400;


    //Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //GUI


        //MIDI Stuff
        loadMidi();
        //TODO: Proper MIDI file picker
            //Create a new MidiProcessor:
        processor = new MidiProcessor(midi);
            //Register for the events you're interested in:
        eventDisplayer = new EventDisplayer(this);
        processor.registerEventListener(eventDisplayer, MidiEvent.class);

        noteTimer = new CountDownTimer(CORRECT_NOTE_COUNTDOWN, 10) {
            public void onTick(long millisUntilFinished) {
                timeLeftForCorrectNote = millisUntilFinished;
            }
            public void onFinish() {
                timeLeftForCorrectNote = -1;
            }
        };

        metronomeTimer = new CountDownTimer(100,100) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                metronomeText.setText("");
            }
        };

        initGui();

    }


    //TODO: reset play head on mode change
    //work the modes into the GUI thread
    protected void onStart(){
        super.onStart();
        Log.d("onStart","method called");
    }

    protected void onStop(){
        super.onStop();
        Log.d("onStop","method called");
//        stopProcessing();
        //TODO: pause/destroy? audio thread
    }

    protected void onPause(){
        super.onPause();
        Log.d("onPause","method called");
//        stopProcessing();
    }

    protected void onResume(){
        //TODO: For some reason this doesn't get called when you switch apps
        //Only gets called when the app is created for the first time
        super.onResume();
        Log.d("onResume","method called");
        //Ask for runtime permissions
        boolean permissionsGranted = askForPermissions();
        Log.d("onResume","permissionsGranted: " + permissionsGranted);
        if(permissionsGranted) {
            Log.d("onResume","resuming");
            startProcessing();
        }

        //TODO: resume/restart? audio thread
    }

    protected void onDestroy(){
        super.onDestroy();
        Log.d("onDestroy","method called");
        stopProcessing();
    }

    //GUI
    private void initGui(){
        eventText = (TextView) findViewById(R.id.eventText);
        correctText = (TextView) findViewById(R.id.correctText);
        mistakeText = (TextView) findViewById(R.id.mistakeText);
        metronomeText = (TextView) findViewById(R.id.metronomeText);
        practiceRadio = (RadioButton) findViewById(R.id.practiceRadio);
        playRadio = (RadioButton) findViewById(R.id.playRadio);
        modeRadioGroup = (RadioGroup) findViewById(R.id.modeRadioGroup);
        fretboard = (FretboardView) findViewById(R.id.fretboard);
        tempoSeek = (SeekBar) findViewById(R.id.tempoSeek);
        tempoSeek.setMax(200);
        tempoSeek.setProgress(100);
        tempoSeek.setSecondaryProgress(100);
        tempoText = (TextView) findViewById(R.id.tempoText);

        tempoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(progress == 0) progress = 1;
                tempoText.setText("Tempo: " + Integer.toString(progress) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if(progress == 0) progress = 1;
                changeTempo(progress);
            }
        });

        modeRadioGroup.clearCheck();
        modeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d("RadioGroup OnChecked",Integer.toString(checkedId));
                if(checkedId == R.id.practiceRadio){
                    Log.d("Practice Mode","enabled");
                    practiceMode = true;
                    tempoSeek.setVisibility(View.INVISIBLE);
                    tempoText.setVisibility(View.INVISIBLE);
                    //Stop play mode
                    processor.stop();
                    //Reset the playhead
                    noteIterator = midi.getTracks().get(1).getEvents().iterator();
                    //Set the next note
                    advanceMidiNote();

                } else if(checkedId == R.id.playRadio){
                    practiceMode = false;
                    tempoSeek.setVisibility(View.VISIBLE);
                    tempoText.setVisibility(View.VISIBLE);
                    Log.d("Play Mode","enabled");
                    processor.reset();
                    processor.start();
                }
            }
        });
        practiceRadio.setChecked(true);

        mistakeText.setText("Added mistakes: " + Integer.toString(mistakes));
    }

    //MIDI
    private void loadMidi(){

//        InputStream is = getResources().openRawResource(R.raw.sample_midi);
//        try {
//            midi = new MidiFile(is);
//        } catch (IOException e) {
//            Log.d("onCreate","MIDI file could not be read");
//            e.printStackTrace();
//        }
//        if(midi != null){
//            Log.d("onCreate","MIDI file read");
//        }

        //Possible MIDI sterilization code
//        int maxEvents = -1;
//        MidiTrack selectedTrack = new MidiTrack();
//        for (int i = 0; i < midi.getTrackCount(); i++) {
//            if(midi.getTracks().get(i).getEventCount() > maxEvents){
//                selectedTrack = midi.getTracks().get(i);
//                maxEvents = midi.getTracks().get(i).getEventCount();
//            }
//        }
//
//        Iterator<MidiEvent> it = selectedTrack.getEvents().iterator();
//        while(it.hasNext()){
//            MidiEvent event = it.next();
//            int note = 40;
//            if(event instanceof NoteOn){
//                note = ((NoteOn) event).getNoteValue();
//            }
//            if(event instanceof NoteOff){
//                note = ((NoteOff) event).getNoteValue();
//            }
//
//            if(note < 40 || note > 68){
//                it.remove();
//            }
//        }
//
            //Don't remove track 0 because it's the tempo track
//        for (int i = midi.getTrackCount() - 1; i > 0; i--) {
//            midi.removeTrack(i);
//        }
//
//        midi.addTrack(selectedTrack);


        //Alternatively we can procedurally generate a MIDI file
        MidiTrack tempoTrack = new MidiTrack();
        MidiTrack noteTrack = new MidiTrack();
        TimeSignature ts = new TimeSignature();
        ts.setTimeSignature(4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION);
        Tempo tempo = new Tempo();
        tempo.setBpm(60);
        tempoTrack.insertEvent(ts);
        tempoTrack.insertEvent(tempo);


        for(int i = 0; i < 29; i++)
        {
            int channel = 0;
            int pitch = 40 + i;
            int velocity = 100;
            long tick = i * 480;
            long duration = 120;

            noteTrack.insertEvent(new NoteOn(tick, channel, pitch, velocity));
            noteTrack.insertEvent(new NoteOff(tick+duration, channel, pitch, 0));
        }

        ArrayList<MidiTrack> tracks = new ArrayList<MidiTrack>();
        tracks.add(tempoTrack);
        tracks.add(noteTrack);

        midi = new MidiFile(MidiFile.DEFAULT_RESOLUTION, tracks);



        //Store all the tempo changes - probably unnecessary but no technical debt for future file loading
        tempoTrack = midi.getTracks().get(0);
        Iterator<MidiEvent> it = tempoTrack.getEvents().iterator();
        int tempoEventCount = 0;
        while(it.hasNext())
        {
            MidiEvent event = it.next();

            if(event instanceof Tempo)
            {
                tempoEventCount++;
            }
        }
        originalBpms = new float[tempoEventCount];

        it = tempoTrack.getEvents().iterator();
        int i = 0;
        while(it.hasNext())
        {
            MidiEvent event = it.next();

            if(event instanceof Tempo)
            {
                Tempo tempoEvent = (Tempo)event;
                originalBpms[i] = tempoEvent.getBpm();
                i++;
            }
        }
    }

    private void changeTempo(int targetTempoPercentage){
        processor.stop();

        MidiTrack tempoTrack = midi.getTracks().get(0);
        Iterator<MidiEvent> it = tempoTrack.getEvents().iterator();
        int i = 0;
        while(it.hasNext())
        {
            MidiEvent event = it.next();
            if(event instanceof Tempo)
            {
                Tempo tempoEvent = (Tempo)event;
                tempoEvent.setBpm(originalBpms[i]*(float)targetTempoPercentage/100);
                i++;
            }
        }

        processor.reset();
        processor.start();

    }

    public double midiNoteToHz(int midiNote){
        return Math.pow(2, ((double)midiNote - 69)/12)*440;
    }

    public double hzToMidiNote(double hertz){

        return 69 + (12 * Math.log(hertz/440)/Math.log(2)) ;
    }

    public String midiNoteToName(int midiNote){
        String[] noteString = new String[] { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
        int octave = (midiNote / 12) - 1;
        int noteIndex = (midiNote % 12);
        return (noteString[noteIndex] + Integer.toString(octave));
    }

    private FretboardPosition midiNoteToFretboardPosition(int note){
        if(note < 40 || note > 68){
            throw new IllegalArgumentException("This note is outside the display range of FretX");
        }
        if(note > 59){
            note++;
        }
        int fret = (note - 40)%5;
        int string = 6 - ((note - 40) / 5);
        //This formula always prefers the open 2nd string to the 4th fret of the 3rd string
        return new FretboardPosition(string,fret);
    }


    //Processing handlers
    private void startAudioThread(){
        //Audio Parameters
        int maxFs = AudioInputHandler.getMaxSamplingFrequency();
        int minBufferSize = AudioInputHandler.getMinBufferSize(maxFs);
        audioInputHandler = new AudioInputHandler(maxFs,minBufferSize);
        int minF0 = 60;
        int frameLength = (int)(2*(float)maxFs/(float)minF0);
        float frameOverlap = 0.5f;
        float yinThreshold = 0.10f;
        //We set the lower bound of pitch detection (minF0) to 60Hz considering the guitar strings
        //The minimum buffer size for YIN must be minT0 * 2, where minT0 is the wavelength corresponding to minF0
        //So the frame length for YIN in samples is: (1/minF0) * 2 * maxFs

        //Create new pitch detector
        yin = new PitchDetectorYin(maxFs,frameLength,Math.round((float)frameLength*frameOverlap),yinThreshold);
        //Patch it to audio handler
        audioInputHandler.addAudioAnalyzer(yin);
        //Start the audio thread
        audioThread = new Thread(audioInputHandler,"Audio Thread");
        audioThread.start();
    }

    private void startGuiThread(){
        guiThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        //Even though YIN is producing a pitch estimate every 16ms, that's too fast for the UI on some devices
                        //So we set it to 25ms, which is good enough
                        Thread.sleep(25);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
//                                if(eventDisplayer.midiEvent instanceof NoteOff){
//                                    NoteOff noteoff = (NoteOff) eventDisplayer.midiEvent;
//                                    int removeNote = noteoff.getNoteValue();
//                                    if(currentMidiNote == removeNote){
//                                        currentMidiNote = -1;
//                                        fretboard.playingCorrectly = false;
//                                    } else {
//                                        //This shouldn't happen, we only use monophonic MIDI tracks for this part of the app
//                                    }
//                                }

                                float pitch = yin.medianPitch;
                                if(currentMidiNote > -1){
                                    eventText.setText("Target: " + midiNoteToName(currentMidiNote) + "\n" +
                                            "You: "
                                    );
                                    if(pitch > -1){
                                        double playedNote = hzToMidiNote(pitch);
                                        double difference = Math.abs(currentMidiNote-playedNote);
                                        eventText.setText("Target: " + midiNoteToName(currentMidiNote) + "\n" +
                                                "You: " + midiNoteToName(Math.round((float)playedNote))
                                        );

                                        if(practiceMode == false){
                                            if(difference < ERROR_THRESHOLD_IN_SEMITONES && timeLeftForCorrectNote > 0 && correctNoteLock){
                                                correctNotes++;
                                                correctNoteLock = false;
                                            }

                                        } else {
                                            if(difference < ERROR_THRESHOLD_IN_SEMITONES){
                                                correctNotes++;
                                                advanceMidiNote();
                                            }
                                        }
                                    } else{
                                        //fretboard.playingCorrectly = false;
                                    }
                                }


                            if(practiceMode == false){
                                if(eventDisplayer.newMetronomeTickArrived){
                                    MetronomeTick metronomeTick = eventDisplayer.getMetronomeTick();
                                    metronomeText.setText("x");
                                    metronomeTimer.start();
                                }

                                if(eventDisplayer.newNoteOnArrived){
                                    NoteOn noteon = eventDisplayer.getNoteOn();
                                    currentMidiNote = noteon.getNoteValue();
                                    FretboardPosition fretboardPosition = midiNoteToFretboardPosition(currentMidiNote);
                                    fretboard.setFretboardPosition(fretboardPosition);
                                    fretboard.drawNotes = true;
                                    //Integer.toString(noteon.getNoteValue()) + "\n" +
                                    eventText.setText("Target: " + midiNoteToName(noteon.getNoteValue()) + "\n" +
//                                            new DecimalFormat("#.##").format(midiNoteToHz(noteon.getNoteValue() )) + " Hz" + "\n" +
//                                            "String: " + fretboardPosition.getString() + " Fret: " + fretboardPosition.getFret()
                                            "You: ");
                                    totalNotes++;
                                    correctNoteLock = true;
                                    noteTimer.start();

                                }
                                if(eventDisplayer.newNoteOffArrived){
                                    mistakeLock = true;
                                    NoteOff noteoff = eventDisplayer.getNoteOff();
                                    if(currentMidiNote == noteoff.getNoteValue()){
                                        currentMidiNote = -1;
                                        fretboard.drawNotes = false;
                                    }
                                }
                                if(currentMidiNote == -1 && pitch > -1 && mistakeLock){
                                    mistakes++;
                                    mistakeLock = false;
                                    mistakeText.setText("Added mistakes: " + Integer.toString(mistakes));
                                }
                            } else {
                                FretboardPosition fretboardPosition = midiNoteToFretboardPosition(currentMidiNote);
                                fretboard.setFretboardPosition(fretboardPosition);
                                fretboard.drawNotes = true;
                            }
                                //correctText.setText(Integer.toString((int)Math.ceil((float)correctNotes / (float)totalNotes * 100)) + "%");
                                correctText.setText(Integer.toString(correctNotes)+ "/" + Integer.toString(totalNotes) + " notes correctly played");

//                                float pitch = yin.result.getPitch();
//                                if(pitch == -1){
//                                } else{
//                                    if(yin.medianPitch > 0){
//                                    }
//                                }
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        if(practiceMode == false) {
            processor.start();
        }
        //TODO: MidiProcessor spawns a new thread with no reference everytime you call this, so those threads can't be killed
        guiThread.start();

    }

    private void advanceMidiNote(){
        while(noteIterator.hasNext())
        {
            MidiEvent event = noteIterator.next();
            if(event instanceof NoteOn)
            {
                int noteValue = ((NoteOn) event).getNoteValue();
                if(noteValue >= 40 && noteValue <= 68) {
                    currentMidiNote = noteValue;
                    totalNotes++;
                    return;
                }
            }
        }
    }

    private void startProcessing(){
        Log.d("startProcessing","method called");
        if(!processingIsRunning){
            startAudioThread();
            startGuiThread();
            processingIsRunning = true;
            Log.d("startProcessing","processes started");
        }

    }

    private void stopProcessing(){
        Log.d("stopProcessing","method called");
        if(processingIsRunning){
            if(audioInputHandler != null){
                audioInputHandler.onDestroy();
                audioInputHandler = null;
            }
            if(yin != null){
                yin = null;
            }
            if(audioThread != null){
                try {
                    audioThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                audioThread = null;
            }
            if(guiThread != null){
                try {
                    guiThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                guiThread = null;
            }
            if(processor != null){
                processor.stop();
            }
            processingIsRunning = false;
            Log.d("stopProcessing","processes stopped");
        }

    }


    //Permissions
    private boolean askForPermissions(){
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (result == PackageManager.PERMISSION_GRANTED){
            return true;
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.RECORD_AUDIO)){
                //If the user has denied the permission previously your code will come to this block
                //Here you can explain why you need this permission
                //Explain here why you need this permission
            }
            //And finally ask for the permission
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CODE_RECORD_AUDIO);
            return false;
        }
    }

    //This method will be called when the user will tap on allow or deny
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //Checking the request code of our request
        if(requestCode == PERMISSION_CODE_RECORD_AUDIO){
            //If permission is granted
            if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                startProcessing();
            }else{
                //Displaying another toast if permission is not granted
                Toast.makeText(this,"FretX Note Detector cannot work without this permission. Restart the app to ask for it again.", Toast.LENGTH_LONG).show();
            }
        }
    }


}

