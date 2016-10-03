package rocks.fretx.notedetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.MidiTrack;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOff;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.event.meta.Tempo;
import com.pdrogfer.mididroid.examples.EventPrinter;
import com.pdrogfer.mididroid.util.MidiProcessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private final int PERMISSION_CODE_RECORD_AUDIO = 42; //This is arbitrary, so why not The Answer to Life, Universe, and Everything?
    AudioInputHandler audioInputHandler;
    private Thread audioThread;
    private Thread guiThread;
    private boolean processingIsRunning = false;
    protected PitchDetectorYin yin;

    MidiFile midi;
    MidiProcessor processor;
    EventDisplayer eventDisplayer;
    float [] originalBpms;

    TextView eventText;
    SeekBar tempoSeek;
    TextView tempoText;
    FretboardView fretboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //GUI
        eventText = (TextView) findViewById(R.id.eventText);
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
        fretboard = (FretboardView) findViewById(R.id.fretboard);

        //MIDI
        loadMidi();

//         Create a new MidiProcessor:
        processor = new MidiProcessor(midi);

//         Register for the events you're interested in:

        eventDisplayer = new EventDisplayer(this);
        processor.registerEventListener(eventDisplayer, MidiEvent.class);




    }

    private void changeTempo(int targetTempoPercentage){
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

    }

    private void loadMidi(){
        InputStream is = getResources().openRawResource(R.raw.sample_midi);
        try {
            midi = new MidiFile(is);
        } catch (IOException e) {
            Log.d("onCreate","MIDI file could not be read");
            e.printStackTrace();
        }
        if(midi != null){
            Log.d("onCreate","MIDI file read");
        }

        //Store all the tempo changes - probably unnecessary but no technical debt
        MidiTrack tempoTrack = midi.getTracks().get(0);
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



//        MidiTrack newTrack = midi.getTracks().get(0);
//        newTrack = getOnlyNotes(newTrack);
//        midi.removeTrack(0);
//        midi.addTrack(newTrack);

    }

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




    private MidiTrack getOnlyNotes(MidiTrack track){
        //Remove any event that is not a note
        Iterator<MidiEvent> it = track.getEvents().iterator();
        List<MidiEvent> eventsToRemove = new ArrayList<MidiEvent>();

        while(it.hasNext())
        {
            MidiEvent event = it.next();

            if(!(event instanceof NoteOn) && !(event instanceof NoteOff))
            {
                eventsToRemove.add(event);
            }
        }

        for(MidiEvent event : eventsToRemove)
        {
            track.removeEvent(event);
        }

        return track;
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
                                if(eventDisplayer.midiEvent instanceof NoteOn){
                                    NoteOn noteon = (NoteOn) eventDisplayer.midiEvent;
                                    FretboardPosition fretboardPosition = midiNoteToFretboardPosition(noteon.getNoteValue());
                                    fretboard.setFretboardPosition(fretboardPosition);

                                    //Integer.toString(noteon.getNoteValue()) + "\n" +
                                    eventText.setText(midiNoteToName(noteon.getNoteValue()) + "\n" +
                                            new DecimalFormat("#.##").format(midiNoteToHz(noteon.getNoteValue() )) + " Hz" + "\n" +
                                            "String: " + fretboardPosition.getString() + " Fret: " + fretboardPosition.getFret()
                                            );

                                }

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
        guiThread.start();
        processor.start();
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
            processingIsRunning = false;
            Log.d("stopProcessing","processes stopped");
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

    //Utility
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

}

