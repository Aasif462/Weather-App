package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.Models.WeatherResponse
import com.example.weatherapp.Network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    private lateinit var mCustomDialog: Dialog
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME , Context.MODE_PRIVATE)

        if(!isLocationEnabled()){
            Toast.makeText(applicationContext, "Please Turn on You GPS", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        else{
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
                .withListener( object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestNewLocationData()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                        showRationalDialog()
                    }
                }).onSameThread().check()
        }


    }

    private fun isLocationEnabled():Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationalDialog(){
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage("It Looks Like You have turned off Permission required"+
                "For this Feature. It can be enabled Under the "+"Application Settings")
        dialog.setPositiveButton("GO TO SETTINGS"){
                dialog_,which ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package",packageName ,null)
                intent.data = uri
                startActivity(intent)
            }
            catch (e: ActivityNotFoundException){
                e.printStackTrace()
            }
        }
        dialog.setNegativeButton("Cancel"){
                dialog,_ ->
            dialog.dismiss()
        }
        dialog.show()

    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData(){
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY
        mLocationRequest.interval = 1000
        mLocationRequest.numUpdates = 1

        mFusedLocationClient.requestLocationUpdates(mLocationRequest , mLocationCallback , Looper.myLooper()!!)
    }

    private val mLocationCallback = object: LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude

            getLocationDetails(latitude , longitude)
        }
    }
    
    private fun getLocationDetails(latitude:Double, longitude:Double){
        if(Constants.isNetworkAvailable(this@MainActivity)){
            val retrofit:Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service:WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall:Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )

            showDialogue()

            listCall.enqueue(object:Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        cancelDialogue()
                        val weatherList: WeatherResponse = response.body()!!
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_DATA , weatherResponseJsonString)
                        editor.apply()
                        setupUI()

                        Log.i("result" ,"$weatherList")
                    }
                    else{
                        when(response.code()){
                            400 -> Log.i("Error 400 " , "Bad Connection")
                            404 -> Log.i("Error 404 " , "Not Found")
                            else -> {
                                Log.i("Error" , "Generic Error")
                            }
                        }

                    }

                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.i("Erroorr" , t.message.toString() )
                    cancelDialogue()
                }

            })
        }
        else{
            Toast.makeText(
                applicationContext,
                "You are Not Connected to Internet",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDialogue(){
        mCustomDialog = Dialog(this@MainActivity)
        mCustomDialog.setContentView(R.layout.progress_dialog)
        mCustomDialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu , menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.refreshBtn-> {
                requestNewLocationData()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun cancelDialogue(){
        mCustomDialog.dismiss()
    }

    @SuppressLint("SetTextI18n", "NewApi")
    private fun setupUI(){

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_DATA , "")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString , WeatherResponse::class.java)

            for (i in weatherList.weather.indices){
                weatherTxt.text = weatherList.weather[i].main
                weather.text = weatherList.weather[i].description

                degreeTxt.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                sunrise.text = unixTime(weatherList.sys.sunrise)
                sunset.text = unixTime(weatherList.sys.sunset)
                windTxt.text = weatherList.wind.speed.toString()
                wind.text = weatherList.wind.deg.toString()
                minimumTxt.text = weatherList.main.temp_min.toString() + " min"
                maximumTxt.text = weatherList.main.temp_mx.toString()  + " max"

                name.text = weatherList.sys.country
                nameTxt.text = weatherList.name

                when(weatherList.weather[i].icon){
                    "01d" -> snowFlake.setImageResource(R.drawable.sunny)
                    "02d" -> snowFlake.setImageResource(R.drawable.cloud)
                    "03d" -> snowFlake.setImageResource(R.drawable.cloud)
                    "04d" -> snowFlake.setImageResource(R.drawable.cloud)
                    "04n" -> snowFlake.setImageResource(R.drawable.cloud)
                    "10d" -> snowFlake.setImageResource(R.drawable.rain)
                    "11d" -> snowFlake.setImageResource(R.drawable.storm)
                    "13d" -> snowFlake.setImageResource(R.drawable.snowflake)
                    "01n" -> snowFlake.setImageResource(R.drawable.cloud)
                    "02n" -> snowFlake.setImageResource(R.drawable.cloud)
                    "03n" -> snowFlake.setImageResource(R.drawable.cloud)
                    "10n" -> snowFlake.setImageResource(R.drawable.cloud)
                    "11n" -> snowFlake.setImageResource(R.drawable.rain)
                    "13n" -> snowFlake.setImageResource(R.drawable.snowflake)
                }
            }
        }


    }

    private fun getUnit(value:String):String
    {
        var value = "°C"

        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }
    
    private fun unixTime(timex:Long):String?{
        val date = Date(timex)
        val sdf = SimpleDateFormat("HH:mm" , Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}