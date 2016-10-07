package rocks.fretx.notedetection;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;

import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.util.MidiEventListener;

/**
 * Created by Onur Babacan on 10/3/16.
 */

//TODO:Rename this class accordingly

// This class will print any event it receives to the console
public class EventDisplayer implements MidiEventListener
{
    private NoteOn noteOn;
    protected long time;
    private MainActivity main;
    protected boolean newNoteArrived;

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
//        System.out.println("Received event: " + event + " | " + Long.toString(ms));

        time = ms;
        if(event instanceof NoteOn){
            System.out.println("Received event: " + event + " | " + Long.toString(ms));
            noteOn = (NoteOn) event;
            newNoteArrived = true;
        }


    }

    public NoteOn getNote(){
        //should reading the data reset the noteOn value?
        newNoteArrived = false;
        return noteOn;
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