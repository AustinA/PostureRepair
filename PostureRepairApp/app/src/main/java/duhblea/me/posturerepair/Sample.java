package duhblea.me.posturerepair;

/**
 * Object representation of a sampled reading
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
     *
     * @return
     * The status
     */
    public Boolean getStatus() {
        return status;
    }

    /**
     *
     * @param status
     * The status
     */
    public void setStatus(Boolean status) {
        this.status = status;
    }

    /**
     *
     * @return
     * The x
     */
    public Double getX() {
        return x;
    }

    /**
     *
     * @param x
     * The x
     */
    public void setX(Double x) {
        this.x = x;
    }

    /**
     *
     * @return
     * The y
     */
    public Double getY() {
        return y;
    }

    /**
     *
     * @param y
     * The y
     */
    public void setY(Double y) {
        this.y = y;
    }

    /**
     *
     * @return
     * The z
     */
    public Double getZ() {
        return z;
    }

    /**
     *
     * @param z
     * The z
     */
    public void setZ(Double z) {
        this.z = z;
    }


    public String toString()
    {
        String retVal = "Status: " + ((status == null) ? "null" : status.toString())
                + " | " + "X: " + ((x == null) ? "null" : x.toString())
                + " | " + "Y: " + ((y == null) ? "null" : y.toString())
                + " | " + "Z: " + ((z == null) ? "null" : z.toString())
                + "\n";

        return retVal;
    }

}