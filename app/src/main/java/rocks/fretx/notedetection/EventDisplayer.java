package rocks.fretx.notedetection;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;

import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOff;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.util.MetronomeTick;
import com.pdrogfer.mididroid.util.MidiEventListener;

/**
 * Created by Onur Babacan on 10/3/16.
 */

//TODO:Rename this class accordingly

// This class will print any event it receives to the console
public class EventDisplayer implements MidiEventListener
{
    private NoteOn noteOn;
    private NoteOff noteOff;
    private MetronomeTick metronomeTick;
    protected long time;
    private MainActivity main;
    protected boolean newNoteOnArrived, newNoteOffArrived, newMetronomeTickArrived;

    public EventDisplayer(MainActivity ma)
    {
        main = ma;
    }

    @Override
    public void onStart(boolean fromBeginning)
    {
        if(fromBeginning)
        {
            System.out.println("Started!");
        }
        else
        {
            System.out.println("Resumed");
        }
    }

    @Override
    public void onEvent(MidiEvent event, long ms)
    {


        time = ms;
        if(event instanceof MetronomeTick){
//            System.out.println("Received event: " + event + " | " + Long.toString(ms));
            newMetronomeTickArrived = true;
            metronomeTick = (MetronomeTick) event;
        }
        if(event instanceof NoteOn){
//            System.out.println("Received event: " + event + " | " + Long.toString(ms));
            noteOn = (NoteOn) event;
            newNoteOnArrived = true;
        }
        if(event instanceof NoteOff){
//            System.out.println("Received event: " + event + " | " + Long.toString(ms));
            noteOff = (NoteOff) event;
            newNoteOffArrived = true;
        }


    }

    public NoteOn getNoteOn(){
        //should reading the data reset the noteOn value?
        newNoteOnArrived = false;
        return noteOn;
    }

    public NoteOff getNoteOff(){
        newNoteOffArrived = false;
        return noteOff;
    }

    public MetronomeTick getMetronomeTick(){
        newMetronomeTickArrived = false;
        return metronomeTick;
    }

    @Override
    public void onStop(boolean finished)
    {
        if(finished)
        {
            System.out.println(" Finished! Restarting...");
            main.processor.stop();
            main.processor.reset();
            main.processor.start();
        }
        else
        {
            System.out.println("Paused");
        }
    }
}