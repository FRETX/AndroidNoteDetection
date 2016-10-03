package rocks.fretx.notedetection;

/**
 * Created by Onur Babacan on 9/23/16.
 */

public interface AudioAnalyzer {

    void process(AudioData audioData);
    void processingFinished();

}
