package duhblea.me.posturerepair;

/**
 * Object representation of a sampled reading of euler angles from the BNO055
 *
 * @author Austin Alderton
 * @version 19 October 2016
 */

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Sample {

    @SerializedName("status")
    @Expose
    private Boolean status;
    @SerializedName("x")
    @Expose
    private Double x;
    @SerializedName("y")
    @Expose
    private Double y;
    @SerializedName("z")
    @Expose
    private Double z;

    /**
     * Configuration status received from the BNO055
     *
     * @return The status
     */
    public Boolean getStatus() {
        return status;
    }

    /**
     * Set Configuration status received from the BNO055
     *
     * @param status The status
     */
    public void setStatus(Boolean status) {
        this.status = status;
    }

    /**
     * Get X angle
     *
     * @return The x
     */
    public Double getX() {
        return x;
    }

    /**
     * Set X angle
     *
     * @param x The x
     */
    public void setX(Double x) {
        this.x = x;
    }

    /**
     * Get Y angle
     *
     * @return The y
     */
    public Double getY() {
        return y;
    }

    /**
     * Set Y angle
     *
     * @param y The y
     */
    public void setY(Double y) {
        this.y = y;
    }

    /**
     * Get Z angle
     *
     * @return The z
     */
    public Double getZ() {
        return z;
    }

    /**
     * Z angle set
     *
     * @param z The z
     */
    public void setZ(Double z) {
        this.z = z;
    }


    /**
     * To String method to print to raw output in Main Activity
     *
     * @return
     */
    @Override
    public String toString() {
        String retVal = "X: " + ((x == null) ? "null" : x.toString())
                + " | " + "Y: " + ((y == null) ? "null" : y.toString())
                + " | " + "Z: " + ((z == null) ? "null" : z.toString())
                + "\n";

        return retVal;
    }

}