package com.cs487.ace;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

/**
 * Created by pidgeonsquish on 11/17/14.
 */
public class AceGameModel {

    private boolean ordinanceLaunched = false;

    private static final String TIME_LABEL = "Liberation Time: ";
    private static final String KILL_COUNT_LABEL = " Commies Liberated";
    private static final String SCORE_LABEL = " Freedom Points";
    private static final String ORDINANCE_READY_LABEL = "READY TO LAUNCH";
    private static final String ORDINANCE_UNAVAILABLE_LABEL = "FREEDOM DISPERSAL IN PROGRESS";

    private static float timeElapsed = 0f;
    private static int killCount = 0;
    private static int score = 0;

    private static StringBuilder concatString = new StringBuilder();

    public AceGameModel(){

    }

    public void setOrdinanceLaunched(boolean eagleOneFoxTwo){
        ordinanceLaunched = eagleOneFoxTwo;
    }

    public void setFreedomSeconds(float freedomSeconds){
        timeElapsed += freedomSeconds;
    }

    public void commieLiberated(){
        killCount++;
    }

    //Consider when new enemies are introduced
    //What kind of point values do we want to assign to them and
    //how will we keep track of what type of enemy is it.
    public void setFreedomPoints(){
        score += 500;
    }
    public void freedomIsNotFree(){
        score -= 100;
    }

    public String getScoreText(){
        concatString.setLength(0);
        if(score == 0) {
            concatString.append(score).append(SCORE_LABEL);
        }else {
            concatString.append(score).append(SCORE_LABEL);
        }
        return concatString.toString();
    }

    public String getTimeElapsedText(){
        concatString.setLength(0);
        concatString.append(TIME_LABEL).append(String.format("%.2f",timeElapsed)).append(" Freedom Seconds");
        return concatString.toString();
    }

    public String getKillCountText(){
        concatString.setLength(0);
        concatString.append(killCount).append(KILL_COUNT_LABEL);
        return concatString.toString();
    }

    public String getOrdinanceStatusText(){
        if(ordinanceLaunched){
            return ORDINANCE_UNAVAILABLE_LABEL;
        }else {
            return ORDINANCE_READY_LABEL;
        }
    }
}
