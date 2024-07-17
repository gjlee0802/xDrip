
/**
 * InfluxDB support from pimeys
 */


package com.eveningoutpost.dexdrip.influxdb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.Calibration;
import com.eveningoutpost.dexdrip.models.UserError.Log;

//import org.influxdb.InfluxDB;
//import org.influxdb.InfluxDBFactory;
//import org.influxdb.dto.BatchPoints;
//import org.influxdb.dto.Point;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.client.WriteApi;

import java.io.IOException;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class InfluxDBUploader {
    private static final int SOCKET_TIMEOUT = 60000;
    private static final int CONNECTION_TIMEOUT = 30000;
    private static final String TAG = InfluxDBUploader.class.getSimpleName();
    private static String last_error;
    private SharedPreferences prefs;
    private String dbName;
    private String dbUri;
    private String dbUser;
    private String dbPassword;
    private OkHttpClient.Builder client;

    public InfluxDBUploader(Context context) {
        //Context mContext = context;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        dbName = prefs.getString("cloud_storage_influxdb_database", null);
        dbUri = prefs.getString("cloud_storage_influxdb_uri", null);
        dbUser = prefs.getString("cloud_storage_influxdb_username", null);
        dbPassword = prefs.getString("cloud_storage_influxdb_password", null);

        client = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        HttpUrl url = HttpUrl.parse(dbUri);
                        String fullPath = (url.encodedPath() + chain.request().url().encodedPath()).replaceFirst("^//", "/");
                        HttpUrl.Builder fixedUrl = chain.request().url().newBuilder().encodedPath(fullPath);
                        return chain.proceed(chain.request().newBuilder().url(fixedUrl.build()).build());
                    }
                });
    }


    // For InfluxDB 1.x
    /*
    public boolean upload(List<BgReading> glucoseDataSets, List<Calibration> meterRecords, List<Calibration> calRecords) {
        try {
            BatchPoints batchPoints = BatchPoints
                    .database(dbName)
                    .retentionPolicy("autogen")
                    .consistency(InfluxDB.ConsistencyLevel.ALL)
                    .build();


            for (BgReading record : glucoseDataSets) {
                if (record == null) {
                    Log.e(TAG, "InfluxDB glucose record is null");
                    continue;
                }
                batchPoints.point(createGlucosePoint(record));
            }

            for (Calibration record : meterRecords) {
                if (record == null) {
                    Log.e(TAG, "InfluxDB meter record is null");
                    continue;
                }
                batchPoints.point(createMeterPoint(record));
            }

            for (Calibration record : calRecords) {
                if (record == null) {
                    Log.e(TAG, "InfluxDB calibration record is null");
                    continue;
                }
                if (record.slope == 0d) continue;
                batchPoints.point(createCalibrationPoint(record));
            }

            try {
                Log.d(TAG, "Influx url: " + dbUri);
                InfluxDBFactory.connect(dbUri, dbUser, dbPassword, client).enableGzip().write(batchPoints);
                last_error = null;
                return true;
            } catch (java.lang.ExceptionInInitializerError e) {
                Log.e(TAG, "InfluxDB failed: " + e.getCause());
                return false;
            } catch (java.lang.NoClassDefFoundError e) {
                Log.e(TAG, "InfluxDB failed more: " + e);
                return false;
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "InfluxDB problem: " + e);
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Write to InfluxDB failed: " + e);
                last_error = e.getMessage();
                return false;
            }
        } catch (Exception e) {
            Log.wtf(TAG, "Exception during initialization: ", e);
            return false;
        }
    }
    */

    // For InfluxDB 2.x
    public boolean upload(List<BgReading> glucoseDataSets, List<Calibration> meterRecords, List<Calibration> calRecords) {
        try {
            // For Influx 2.x
            InfluxDBClient influxDBClient = InfluxDBClientFactory.create(dbUri, "my-token".toCharArray());
            String bucket = "cgm-engineering-mode"; // 버킷 이름을 여기에 설정하십시오
            String org = "iclab"; // 조직 이름을 여기에 설정하십시오
            WriteApi writeApi = influxDBClient.getWriteApi();

            for (BgReading record : glucoseDataSets) {
                if (record == null) {
                    Log.e(TAG, "InfluxDB glucose record is null");
                    continue;
                }
                writeApi.writePoint(bucket, org, createGlucosePoint(record));
            }

            for (Calibration record : meterRecords) {
                if (record == null) {
                    Log.e(TAG, "InfluxDB meter record is null");
                    continue;
                }
                writeApi.writePoint(bucket, org, createMeterPoint(record));
            }

            for (Calibration record : calRecords) {
                if (record == null) {
                    Log.e(TAG, "InfluxDB calibration record is null");
                    continue;
                }
                if (record.slope == 0d) continue;
                writeApi.writePoint(bucket, org, createCalibrationPoint(record));
            }

            influxDBClient.close();
            last_error = null;
            return true;
        } catch (java.lang.ExceptionInInitializerError e) {
            Log.e(TAG, "InfluxDB failed: " + e.getCause());
            return false;
        } catch (java.lang.NoClassDefFoundError e) {
            Log.e(TAG, "InfluxDB failed more: " + e);
            return false;
        } catch (IllegalArgumentException e) {
            Log.wtf(TAG, "InfluxDB problem: " + e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Write to InfluxDB failed: " + e);
            last_error = e.getMessage();
            return false;
        }
    }


    private Point createGlucosePoint(BgReading record) {
        final BigDecimal delta = new BigDecimal(record.calculated_value_slope * 5 * 60 * 1000)
                .setScale(3, BigDecimal.ROUND_HALF_UP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Point.measurement("glucose")
                    .addTag("unit", "mg/dL") // Add any relevant tags
                    .addField("value_mmol", record.calculated_value_mmol())
                    .addField("value_mgdl", record.getMgdlValue())
                    .addField("direction", record.slopeName())
                    .addField("filtered", record.ageAdjustedFiltered() * 1000)
                    .addField("unfiltered", record.usedRaw() * 1000)
                    .addField("rssi", 100)
                    .addField("noise", record.noiseValue())
                    .addField("delta", delta)
                    .time(Instant.ofEpochMilli(record.getEpochTimestamp()), WritePrecision.MS); // Use Instant and WritePrecision
        }
        return null;
    }

    private Point createMeterPoint(Calibration record) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Point.measurement("meter")
                    .addTag("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"))
                    .addTag("type", "mbg")
                    .addField("mbg", record.bg)
                    .time(Instant.ofEpochMilli(record.timestamp), WritePrecision.MS); // Use Instant and WritePrecision
        }
        return null;
    }

    private Point createCalibrationPoint(Calibration record) {
        Point builder = null; // Use Instant and WritePrecision
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = Point.measurement("calibration")
                    .addTag("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"))
                    .addTag("type", "cal")
                    .time(Instant.ofEpochMilli(record.timestamp), WritePrecision.MS);
        }

        if (record.check_in) {
            assert builder != null;
            builder.addField("slope", record.first_slope)
                    .addField("intercept", record.first_intercept)
                    .addField("scale", record.first_scale);
        } else {
            assert builder != null;
            builder.addField("slope", (1000 / record.slope))
                    .addField("intercept", ((record.intercept * -1000) / record.slope))
                    .addField("scale", 1);
        }

        return builder;//builder.build();
    }

    /*
    private Point createGlucosePoint(BgReading record) {
        // TODO DisplayGlucose option
        final BigDecimal delta = new BigDecimal(record.calculated_value_slope * 5 * 60 * 1000)
                .setScale(3, BigDecimal.ROUND_HALF_UP);

        return Point.measurement("glucose")
                .time(record.getEpochTimestamp(), TimeUnit.MILLISECONDS)
                .addField("value_mmol", record.calculated_value_mmol())
                .addField("value_mgdl", record.getMgdlValue())
                .addField("direction", record.slopeName())
                .addField("filtered", record.ageAdjustedFiltered() * 1000)
                .addField("unfiltered", record.usedRaw() * 1000)
                .addField("rssi", 100)
                .addField("noise", record.noiseValue())
                .addField("delta", delta)
                .build();
    }

    private Point createMeterPoint(Calibration record) {
        return Point.measurement("meter")
                .time(record.timestamp, TimeUnit.MILLISECONDS)
                .tag("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"))
                .tag("type", "mbg")
                .addField("mbg", record.bg)
                .build();
    }

    private Point createCalibrationPoint(Calibration record) {
        Point.Builder builder = Point.measurement("calibration")
                .time(record.timestamp, TimeUnit.MILLISECONDS)
                .tag("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"))
                .tag("type", "cal");

        if (record.check_in) {
            builder.addField("slope", record.first_slope)
                    .addField("intercept", record.first_intercept)
                    .addField("scale", record.first_scale);
        } else {
            builder.addField("slope", (1000 / record.slope))
                    .addField("intercept", ((record.intercept * -1000) / record.slope))
                    .addField("scale", 1);
        }

        return builder.build();
    }
    */
}