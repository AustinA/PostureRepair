package duhblea.me.posturerepair;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to aid in the creation and editing a database of samples
 *
 * @author Austin Alderton
 * @version 13 November 2016
 */

public class SampleDatabaseHelper extends SQLiteOpenHelper {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss:SSS");

    // Database information
    private static final String DB_NAME = "SamplesDB";
    private static final String TABLE_NAME = "Samples";
    private static final int DB_VERSION = 1;

    // Columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_SAMPLE_TIME = "sampleTime";
    private static final String COLUMN_X = "Euler_X";
    private static final String COLUMN_Y = "Euler_Y";
    private static final String COLUMN_Z = "Euler_Z";

    // Creation statement
    private static final String TABLE_CREATE =
            "CREATE TABLE " +TABLE_NAME
                    + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_SAMPLE_TIME + " INTEGER, "
                    + COLUMN_X + " REAL, "
                    + COLUMN_Y + " REAL, "
                    + COLUMN_Z + " REAL);";


    /**
     * Default Constructor
     *
     * @param ctx application context
     */
    public SampleDatabaseHelper(Context ctx)
    {
        super(ctx, DB_NAME, null, DB_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        sqLiteDatabase.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

        String sql = "DROP TABLE IF EXISTS Samples";

        sqLiteDatabase.execSQL(sql);

        onCreate(sqLiteDatabase);
    }

    /**
     * Write a sample to the database
     *
     * @param theSample the sample to write
     */
    public boolean writeSample(Long sampleTime, Sample theSample)
    {
        long retVal;

        SQLiteDatabase sqLiteDatabase = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put(COLUMN_SAMPLE_TIME, sampleTime);
        values.put(COLUMN_X, theSample.getX());
        values.put(COLUMN_Y, theSample.getY());
        values.put(COLUMN_Z, theSample.getZ());

        retVal = sqLiteDatabase.insert(TABLE_NAME, null, values);

        sqLiteDatabase.close();

        return (retVal > -1);
    }

    /**
     * Write HashMap of samples to database
     *
     * @param theSamples Hash Map of samples to write
     */
    public void writeSamples(HashMap<Long, Sample> theSamples)
    {

        SQLiteDatabase sqLiteDatabase = this.getWritableDatabase();

        for (Map.Entry<Long, Sample> entry : theSamples.entrySet())
        {
            ContentValues values = new ContentValues();

            values.put(COLUMN_SAMPLE_TIME, entry.getKey());
            values.put(COLUMN_X, entry.getValue().getX());
            values.put(COLUMN_Y, entry.getValue().getY());
            values.put(COLUMN_Z, entry.getValue().getZ());

            sqLiteDatabase.insert(TABLE_NAME, null, values);

        }

        sqLiteDatabase.close();
    }

    /**
     * Create HashMap from database
     */
    public HashMap<Long, Sample> readSamples()
    {
        HashMap<Long, Sample> retVal = new HashMap<Long, Sample>();

        SQLiteDatabase sqLiteDatabase = this.getReadableDatabase();

        String sql = "SELECT * FROM " + TABLE_NAME;

        Cursor cursor = sqLiteDatabase.rawQuery(sql, null);

        try
        {
            while (cursor.moveToNext()) {
                Long sampleTime = cursor.getLong(cursor.getColumnIndex(COLUMN_SAMPLE_TIME));

                Sample theSample = new Sample();

                theSample.setStatus(true);
                theSample.setX(cursor.getDouble(cursor.getColumnIndex(COLUMN_X)));
                theSample.setY(cursor.getDouble(cursor.getColumnIndex(COLUMN_Y)));
                theSample.setZ(cursor.getDouble(cursor.getColumnIndex(COLUMN_Z)));

                retVal.put(sampleTime, theSample);
            }
        }
        finally {
            cursor.close();
        }

        return retVal;
    }

    /**
     * Generate a string representation of database contents
     */
    public String stringOfSamples()
    {
        String retVal = "";

        HashMap<Long, Sample> samples = readSamples();

        for (Map.Entry<Long, Sample> entry : samples.entrySet())
        {
            retVal = retVal + DATE_FORMAT.format(new Date(entry.getKey())) +
                    "  " + entry.getValue().toString();

        }

        return retVal;
    }

}
