package testing.hyyrynen.fredrik.beatrecognitionmoment;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    @Override
    public boolean onTouchEvent(MotionEvent e){
        switch (e.getAction()){
            case MotionEvent.ACTION_DOWN:
                startMoment();
                break;
            default:
                break;
        }
        return true;
    }

    private void startMoment(){
        Intent momentIntent = new Intent(this, BeatMoment.class);
        startActivity(momentIntent);
    }
}
