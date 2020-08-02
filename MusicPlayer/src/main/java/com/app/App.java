package com.app;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        try
        {
            new PlayerController().start();
            
            MusicPlayer mp = new MusicPlayer();

            mp.run();
            
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
