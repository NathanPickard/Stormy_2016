package apps.nathanpickard.stormy2016.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import apps.nathanpickard.stormy2016.R;
import apps.nathanpickard.stormy2016.weather.Current;
import apps.nathanpickard.stormy2016.weather.Day;
import apps.nathanpickard.stormy2016.weather.Forecast;
import apps.nathanpickard.stormy2016.weather.Hour;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String DAILY_FORECAST = "DAILY_FORECAST";
    public static final String HOURLY_FORECAST = "HOURLY_FORECAST";
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final String PREFS_FILE = "apps.nathanpickard.stormy2016.preferences";
    private static final String KEY_LOCATION = "key_location";
    private SharedPreferences.Editor mEditor;
    private SharedPreferences mSharedPreferences;

    private Forecast mForecast;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    @Bind(R.id.timeLabel) TextView mTimeLabel;
    @Bind(R.id.temperatureLabel) TextView mTemperatureLabel;
    @Bind(R.id.apparentTemperatureLabel) TextView mApparentTemperatureLabel;
    @Bind(R.id.humidityValue) TextView mHumidityValue;
    @Bind(R.id.precipValue) TextView mPrecipValue;
    @Bind(R.id.summaryLabel) TextView mSummaryLabel;
    @Bind(R.id.iconImageView) ImageView mIconImageView;
    @Bind(R.id.refreshImageView) ImageView mRefreshImageView;
    @Bind(R.id.progressBar) ProgressBar mProgressBar;
    Spinner spinner;

    double mCurrentLatitude;
    double mCurrentLongitude;
    public String mLocation;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mSharedPreferences = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();


        spinner = (Spinner) findViewById(R.id.spinner);

        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.locations, R.layout.custom_spinner_item);
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        spinner.setSelection(mSharedPreferences.getInt("KEY_LOCATION", 0));

        createClient();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_LOW_POWER)
                .setInterval(10 * 1000)         // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000);  // 1 second, in milliseconds


        mProgressBar.setVisibility(View.INVISIBLE);

        mCurrentLatitude = 45.5200;
        mCurrentLongitude = -122.6819;

        mRefreshImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getForecast(mCurrentLatitude, mCurrentLongitude);
            }
        });

        getForecast(mCurrentLatitude, mCurrentLongitude);

        Log.d(TAG, "Main UI code is running!");
    }


    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        int selectedPosition = spinner.getSelectedItemPosition();
        mEditor.putInt("KEY_LOCATION", selectedPosition);
        mEditor.apply();


        mGoogleApiClient.disconnect();
    }


    private void getForecast(double latitude, double longitude) {
        String apiKey = "c084262a63603f46bcc265a0c5acb6e1";
        String forecastUrl = "https://api.forecast.io/forecast/" + apiKey +
                "/" + latitude + "," + longitude;


        if (isNetworkAvailable()) {
            toggleRefresh();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(forecastUrl)
                    .build();

            Call call = client.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });
                    alertUserAboutError();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });

                    try {
                        String jsonData = response.body().string();
                        Log.v(TAG, jsonData);
                        if (response.isSuccessful()) {
                            mForecast = parseForecastDetails(jsonData);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateDisplay();
                                }
                            });
                        } else {
                            alertUserAboutError();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception caught: ", e);
                    } catch (JSONException e) {
                        Log.e(TAG, "Exception caught: ", e);
                    }
                }
            });
        } else {
            Toast.makeText(this, R.string.network_unavailable_message,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void toggleRefresh() {
        if (mProgressBar.getVisibility() == View.INVISIBLE) {
            mProgressBar.setVisibility(View.VISIBLE);
            mRefreshImageView.setVisibility(View.INVISIBLE);
        } else {
            mProgressBar.setVisibility(View.INVISIBLE);
            mRefreshImageView.setVisibility(View.VISIBLE);
        }
    }

    private void updateDisplay() {
        Current current = mForecast.getCurrent();

        mTemperatureLabel.setText(current.getTemperature() + "");
        mApparentTemperatureLabel.setText(current.getApparentTemperature() + "");
        mTimeLabel.setText("At " + current.getFormattedTime() + " it is");     //("At " + current.getFormattedTime() + " it will be");
        mHumidityValue.setText(current.getHumidity() + "");
        mPrecipValue.setText(current.getPrecipChance() + "%");
        mSummaryLabel.setText(current.getSummary());

        Drawable drawable = getResources().getDrawable(current.getIconId());
        mIconImageView.setImageDrawable(drawable);

        YoYo.with(Techniques.Pulse)
                .duration(700)
                .playOn(findViewById(R.id.temperatureLabel));
    }

    private Forecast parseForecastDetails(String jsonData) throws JSONException {
        Forecast forecast = new Forecast();

        forecast.setCurrent(getCurrentDetails(jsonData));
        forecast.setHourlyForecast(getHourlyForecast(jsonData));
        forecast.setDailyForecast(getDailyForeast(jsonData));

        return forecast;
    }

    private Day[] getDailyForeast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject daily = forecast.getJSONObject("daily");
        JSONArray data = daily.getJSONArray("data");

        Day[] days = new Day[data.length()];

        for (int i = 0; i < data.length(); i++) {
            JSONObject jsonDay = data.getJSONObject(i);
            Day day = new Day();

            day.setSummary(jsonDay.getString("summary"));
            day.setIcon(jsonDay.getString("icon"));
            day.setTemperatureMax(jsonDay.getDouble("temperatureMax"));
            day.setTemperatureMin(jsonDay.getDouble("temperatureMin"));
            day.setTime(jsonDay.getLong("time"));
            day.setTimezone(timezone);

            days[i] = day;
        }

        return days;
    }

    private Hour[] getHourlyForecast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONArray data = hourly.getJSONArray("data");

        Hour[] hours = new Hour[data.length()];

        for (int i = 0; i < data.length(); i++) {
            JSONObject jsonHour = data.getJSONObject(i);
            Hour hour = new Hour();

            hour.setSummary(jsonHour.getString("summary"));
            hour.setTemperature(jsonHour.getDouble("temperature"));
            hour.setPrecipPossibility(jsonHour.getDouble("precipProbability"));
            hour.setIcon(jsonHour.getString("icon"));
            hour.setTime(jsonHour.getLong("time"));
            hour.setTimezone(timezone);

            hours[i] = hour;
        }

        return hours;
    }

    private Current getCurrentDetails(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        Log.i(TAG, "From JSON: " + timezone);

        JSONObject currently = forecast.getJSONObject("currently");

        Current current = new Current();
        current.setHumidity(currently.getDouble("humidity"));
        current.setTime(currently.getLong("time"));
        current.setIcon(currently.getString("icon"));
        current.setPrecipChance(currently.getDouble("precipProbability"));
        current.setSummary(currently.getString("summary"));
        current.setTemperature(currently.getDouble("temperature"));
        current.setApparentTemperature(currently.getDouble("apparentTemperature"));
        current.setTimeZone(timezone);

        Log.d(TAG, current.getFormattedTime());

        return current;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            isAvailable = true;
        }

        return isAvailable;
    }

    private void alertUserAboutError() {
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    @OnClick(R.id.dailyButton)
    public void startDailyActivity(View view) {
        Intent intent = new Intent(this, DailyForecastActivity.class);
        intent.putExtra(DAILY_FORECAST, mForecast.getDailyForecast());
        intent.putExtra(getString(R.string.location_name), mLocation);
        startActivity(intent);

        overridePendingTransition(R.transition.animation1, R.transition.animation2);
    }

    @OnClick(R.id.hourlyButton)
    public void startHourlyActivity(View view) {
        Intent intent = new Intent(this, HourlyForecastActivity.class);
        intent.putExtra(HOURLY_FORECAST, mForecast.getHourlyForecast());
        startActivity(intent);
    }



    // LOCATION TRACKING


    protected void createClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, (LocationListener) this);
        }
        else {
            handleNewLocation(location);
        }
    }

    private void handleNewLocation (Location location) {

        Log.d(TAG, location.toString());

        double mCurrentLatitude = location.getLatitude();
        double mCurrentLongitude = location.getLongitude();
        LatLng latLng = new LatLng(mCurrentLatitude, mCurrentLongitude);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        //Portland coordinates
        if(position == 0) {
            mLocation = "Portland, OR";
            mCurrentLatitude = 45.5200;
            mCurrentLongitude = -122.6819;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //Beaverton coordinates
        if(position == 1) {
            mLocation = "Beaverton, OR";
            mCurrentLatitude = 45.4869;
            mCurrentLongitude = -122.8036;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //Hillsboro coordinates
        if(position == 2) {
            mLocation = "Hillsboro, OR";
            mCurrentLatitude = 45.5228;
            mCurrentLongitude = -122.9897;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //Gresham coordinates
        if (position == 3) {
            mLocation = "Gresham, OR";
            mCurrentLatitude = 45.5036;
            mCurrentLongitude = -122.4394;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //Tigard coordinates
        if(position == 4) {
            mLocation = "Tigard,OR";
            mCurrentLatitude = 45.4278;
            mCurrentLongitude = -122.7789;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //Lake Oswego coordinates
        if (position == 5) {
            mLocation = "Lake Oswego, OR";
            mCurrentLatitude = 45.4196;
            mCurrentLongitude = -122.6676;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //Milwaukie coordinates
        if (position == 6) {
            mLocation = "Milwaukie, OR";
            mCurrentLatitude = 45.4461;
            mCurrentLongitude = -122.6392;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

        //PDX Airport coordinates
        if (position == 7) {
            mLocation = "PDX Airport";
            mCurrentLatitude = 45.5886;
            mCurrentLongitude = -122.5975;

            getForecast(mCurrentLatitude, mCurrentLongitude);
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
