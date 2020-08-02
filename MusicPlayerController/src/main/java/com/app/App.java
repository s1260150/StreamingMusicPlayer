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
            new PlayerController().run();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
