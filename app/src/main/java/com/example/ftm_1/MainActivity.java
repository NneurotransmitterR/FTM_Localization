package com.example.ftm_1;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;

import android.os.Handler;
import android.util.Log;
import android.util.TimeUtils;
import android.view.View;

import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.linear.*;

import com.example.ftm_1.TFLiteModel;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
    // 定位权限
    private boolean mLocationPermissionApproved = false;
    // 超时的最大测量次数
    private int MAX_RANGING_RETRY_COUNT = 3;
    private int mRangingRetryCount = 1;
    // 0: 没有数据（初始状态）
    // 1: 部分测距成功
    // 2: 全部测距成功
    // -1: 测距未成功onRangingFailure回调
    // -2: 测距未成功onRangingResults回调
    private int mFlagRangeSuccess = 0;

    private final Object lock = new Object();
    private boolean isRangingResultReady = false;


    final Handler mRangeRequestDelayHandler = new Handler();
    private int mMillisecondsDelayBeforeNewRangingRequest = 100;
    private static final int MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT = 100;

    private int MAX_MULTI_RANGE_COUNT = 10;
    private int mMultiRangeCount = 0;
    private RangingRequest mRangingRequest;

    private WifiManager mWifiManager;
    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;
    private RttRangingResultCallback_Multi mRttRangingResultCallback_Multi;

    TextView text_output;
    TextView text_FTM_result_1, text_FTM_result_2, text_FTM_result_3, text_FTM_result_4;

    Button btn_check, btn_range;

    Context context;

    // 所有AP的MAC地址
    List<String> macAddress = new ArrayList<>();

    // TODO: 些什么list，就给我写死
    String macAddress_1 = "34:85:18:8f:1a:19";
    String macAddress_2 = "34:85:18:8f:42:21";
    String macAddress_3 = "34:85:18:95:f9:79";
    String macAddress_4 = "34:85:18:8f:19:c1";

    int totalAP = 4;

    // 是否扫描了AP
    private boolean mScanningAP = false;
    // 扫描结果
    List<ScanResult> scanResults = new ArrayList<>();

    // 测距结果
    List<RangingResult> rangingResultsAAA = new ArrayList<>();

    // TODO：结果也一样，给我写死
    List<RangingResult> rangingResults_1 = new ArrayList<>();
    List<RangingResult> rangingResults_2 = new ArrayList<>();
    List<RangingResult> rangingResults_3 = new ArrayList<>();
    List<RangingResult> rangingResults_4 = new ArrayList<>();

    List<RangingResult> tmpRangingResults = new ArrayList<>();
    List<RangingResult> tmpRangingResults_1 = new ArrayList<>();
    List<RangingResult> tmpRangingResults_2 = new ArrayList<>();
    List<RangingResult> tmpRangingResults_3 = new ArrayList<>();
    List<RangingResult> tmpRangingResults_4 = new ArrayList<>();

    private double x1 = 0.0, y1 = 1.858, z1 = 1.385;
    private double x2 = 1.036, y2 = 1.094, z2 = 1.053;
    private double x3 = 3.655, y3 = 1.502, z3 = 0.681;
    private double x4 = 2.540, y4 = 0.195, z4 = 0.998;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化控件
        initView();

        mWifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        // 获取WifiRttManager服务
        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

        mRttRangingResultCallback = new RttRangingResultCallback();
        mRttRangingResultCallback_Multi = new RttRangingResultCallback_Multi();

        // TODO：暂时在变量里面写死，这部分用不到
        // 初始化所有AP的MAC地址
        macAddress.add("34:85:18:8f:1a:19");    // FTM1
        macAddress.add("34:85:18:8f:42:21");    // FTM2
        macAddress.add("34:85:18:95:f9:79");    // FTM3
        macAddress.add("34:85:18:8f:19:c1");    // FTM4

        // 扫描所有AP
        startScanAP();

//        startFTMRanging_Allap();
//        clearRangingResults();
    }

    // 检查是否支持RTT
    private boolean isRTTavailable()
    {
        return this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
    }

    private void startScanAP()
    {
        // 检查是否有定位权限，没有就申请
        if (!mLocationPermissionApproved)
        {
            Log.d("Debug", "Request for location permission");
            // On 23+ (M+) devices, fine location permission not granted. Request permission.
            Intent startIntent = new Intent(this, LocationPermissionRequestActivity.class);
            startActivity(startIntent);
        }

        // 还是没有定位权限就寄
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            text_output.setText("Don't have location permission");
        }

        // 开始扫描AP
        mWifiManager.startScan();
        Log.d("Debug", "Start scan AP");
        // 获取扫描结果
        // TODO:检查是否扫描成功
        scanResults = mWifiManager.getScanResults();
        mScanningAP = true;
        Log.d("Debug", "Get AP scan results");
    }

    private void restartAPScanAndRangingResults()
    {
        // 重新扫描AP
        startScanAP();
        // 清空测距结果
        rangingResults_1.clear();
        rangingResults_2.clear();
        rangingResults_3.clear();
        rangingResults_4.clear();
    }

    private void clearRangingResults()
    {
        // 清空测距结果
        rangingResults_1.clear();
        rangingResults_2.clear();
        rangingResults_3.clear();
        rangingResults_4.clear();
    }

    private void clearTmpRangingResults()
    {
        // 清空测距结果
        tmpRangingResults_1.clear();
        tmpRangingResults_2.clear();
        tmpRangingResults_3.clear();
        tmpRangingResults_4.clear();
    }

    @SuppressLint("MissingPermission")
    private void startFTMRanging()
    {
        if (!mScanningAP)
        {
            // 如果没有扫描AP，就先扫描AP
            startScanAP();
        }

        clearTmpRangingResults();

        // 构建RangingRequest Builder
        RangingRequest.Builder builder = new RangingRequest.Builder();

        Log.d("Debug", "Adding AP to builder");
        // 遍历扫描结果，匹配MAC地址
        for (ScanResult scanResult : scanResults)
        {
            if (scanResult.BSSID.equals(macAddress_1) & totalAP >= 1)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_1);
                builder.addAccessPoint(scanResult);
            } else if (scanResult.BSSID.equals(macAddress_2) & totalAP >= 2)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_2);
                builder.addAccessPoint(scanResult);
            } else if (scanResult.BSSID.equals(macAddress_3) & totalAP >= 3)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_3);
                builder.addAccessPoint(scanResult);
            } else if (scanResult.BSSID.equals(macAddress_4) & totalAP >= 4)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_4);
                builder.addAccessPoint(scanResult);
            }
        }
        Log.d("Debug", "builder:" + builder);

        // 构建请求
        mRangingRequest = builder.build();

        mFlagRangeSuccess = 0;
        mRangingRetryCount = 1;

        // 启动测距
        Log.d("Debug", "wifiRttManager is not NULL, start ranging");
        mWifiRttManager.startRanging(mRangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback);
    }

    @SuppressLint("MissingPermission")
    private void startFTMRanging_MultiRequest()
    {
        if (!mScanningAP)
        {
            // 如果没有扫描AP，就先扫描AP
            startScanAP();
        }

        clearTmpRangingResults();

        // 构建RangingRequest Builder
        RangingRequest.Builder builder = new RangingRequest.Builder();

        Log.d("Debug", "Adding AP to builder");
        // 遍历扫描结果，匹配MAC地址
        for (ScanResult scanResult : scanResults)
        {
            if (scanResult.BSSID.equals(macAddress_1) & totalAP >= 1)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_1);
                builder.addAccessPoint(scanResult);
            } else if (scanResult.BSSID.equals(macAddress_2) & totalAP >= 2)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_2);
                builder.addAccessPoint(scanResult);
            } else if (scanResult.BSSID.equals(macAddress_3) & totalAP >= 3)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_3);
                builder.addAccessPoint(scanResult);
            } else if (scanResult.BSSID.equals(macAddress_4) & totalAP >= 4)
            {
                Log.d("Debug", "Adding MAC:" + macAddress_4);
                builder.addAccessPoint(scanResult);
            }
        }
        Log.d("Debug", "builder:" + builder);

        // 构建请求
        mRangingRequest = builder.build();

        mFlagRangeSuccess = 0;
        mRangingRetryCount = 1;

        // 清空测距数量
        mMultiRangeCount = 0;
        // 启动测距
        Log.d("Debug", "wifiRttManager is not NULL, start ranging");
        mWifiRttManager.startRanging(mRangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback_Multi);
    }


    private void calculateAndShowLocation()
    {
        //调用getCoordinates()函数计算坐标
        List<Double> coordinates = getCoordinates();
        Log.d("Location", "Coordinates: " + coordinates.get(0) + ", " + coordinates.get(1) + ", " + coordinates.get(2));
//
        // 输出结果，后期可以改为任何对结果的处理
//        text_output.setText("Ranging finished:\n");
        text_FTM_result_1.setText("AP1: " + rangingResults_1.get(rangingResults_1.size() - 1).getDistanceMm());
        text_FTM_result_2.setText("AP2: " + rangingResults_2.get(rangingResults_2.size() - 1).getDistanceMm());
        text_FTM_result_3.setText("AP3: " + rangingResults_3.get(rangingResults_3.size() - 1).getDistanceMm());
        text_FTM_result_4.setText("AP4: " + rangingResults_4.get(rangingResults_4.size() - 1).getDistanceMm());
    }

    private void initView()
    {
        text_output = findViewById(R.id.textView);
        text_output.setText("Press check to check RTT");

        text_FTM_result_1 = findViewById(R.id.text_FTM_result_1);
        text_FTM_result_2 = findViewById(R.id.text_FTM_result_2);
        text_FTM_result_3 = findViewById(R.id.text_FTM_result_3);
        text_FTM_result_4 = findViewById(R.id.text_FTM_result_4);
        text_FTM_result_1.setText("AP1: No Data");
        text_FTM_result_2.setText("AP2: No Data");
        text_FTM_result_3.setText("AP3: No Data");
        text_FTM_result_4.setText("AP4: No Data");

        btn_check = (Button) findViewById(R.id.Btn_check);
        btn_range = (Button) findViewById(R.id.Btn_range);

        btn_check.setOnClickListener(this);
        btn_range.setOnClickListener(this);

        context = getApplicationContext();

        if (mWifiRttManager != null)
        {
            // 如果WifiRttManager服务不可用
            text_output.setText("WifiRttManager not available");
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onClick(View view)
    {
        int viewID = view.getId();
        if (viewID == R.id.Btn_check)
        {
            if (isRTTavailable())
            {
                text_output.setText("RTT is available");
            } else
            {
                text_output.setText("RTT is not available");
            }
            restartAPScanAndRangingResults();
        } else if (viewID == R.id.Btn_range)
        {
            // 按下range按钮后，进行测距
            text_output.setText("Start ranging");

            clearRangingResults();
            startFTMRanging_MultiRequest();

            // TODO: 目前看同步问题不好解决，以下内容全部改到onRangingResults()里面

//            //调用getCoordinates()函数计算坐标
//            List<Double> coordinates = getCoordinates();
//            Log.d("Location", "Coordinates: " + coordinates.get(0) + ", " + coordinates.get(1) + ", " + coordinates.get(2));
//
//            // 输出结果，后期可以改为任何对结果的处理
//            text_output.setText("Ranging finished:\n");
//            text_FTM_result_1.setText("AP1: " + rangingResults_1.get(rangingResults_1.size() - 1).getDistanceMm());
//            text_FTM_result_2.setText("AP2: " + rangingResults_2.get(rangingResults_2.size() - 1).getDistanceMm());
//            text_FTM_result_3.setText("AP3: " + rangingResults_3.get(rangingResults_3.size() - 1).getDistanceMm());
//            text_FTM_result_4.setText("AP4: " + rangingResults_4.get(rangingResults_4.size() - 1).getDistanceMm());

        }
    }

    public List<Double> getCoordinates()
    {
        List<Double> coordinates = new ArrayList<Double>();
        //使用最小二乘法计算坐标，需要四个AP测得的距离以及四个AP的坐标
        //每个rangingResults是一个list，求测得的平均值
        double d1 = 0.0, d2 = 0.0, d3 = 0.0, d4 = 0.0;
        for (int i = 1; i < rangingResults_1.size(); i++)
        {
            d1 += rangingResults_1.get(i).getDistanceMm() / 1000.0;
        }
        for (int i = 0; i < rangingResults_2.size(); i++)
        {
            d2 += rangingResults_2.get(i).getDistanceMm() / 1000.0;
        }
        for (int i = 0; i < rangingResults_3.size(); i++)
        {
            d3 += rangingResults_3.get(i).getDistanceMm() / 1000.0;
        }
        for (int i = 0; i < rangingResults_4.size(); i++)
        {
            d4 += rangingResults_4.get(i).getDistanceMm() / 1000.0;
        }
        d1 /= rangingResults_1.size();
        d2 /= rangingResults_2.size();
        d3 /= rangingResults_3.size();
        d4 /= rangingResults_4.size();

        //使用Apache Commons Math包，由最小二乘法推导得到的公式计算坐标
        //构造系数矩阵
        double[][] matrixAData = {{2 * (x2 - x1), 2 * (y2 - y1), 2 * (z2 - z1)}, {2 * (x3 - x2), 2 * (y3 - y2), 2 * (z3 - z2)}, {2 * (x4 - x3), 2 * (y4 - y3), 2 * (z4 - z3)}};
        double[][] matrixBData = {{d1 * d1 - d2 * d2 - x1 * x1 - y1 * y1 - z1 * z1 + x2 * x2 + y2 * y2 + z2 * z2}, {d2 * d2 - d3 * d3 - x2 * x2 - y2 * y2 - z2 * z2 + x3 * x3 + y3 * y3 + z3 * z3},
                {d3 * d3 - d4 * d4 - x3 * x3 - y3 * y3 - z3 * z3 + x4 * x4 + y4 * y4 + z4 * z4}};

        RealMatrix A = new Array2DRowRealMatrix(matrixAData);
        RealMatrix B = new Array2DRowRealMatrix(matrixBData);
        RealMatrix A_TA = A.transpose().multiply(A);
        RealMatrix A_TA_inverse = MatrixUtils.inverse(A_TA);
        RealMatrix X = A_TA_inverse.multiply(A.transpose().multiply(B));

        coordinates.add(X.getEntry(0, 0));
        coordinates.add(X.getEntry(1, 0));
        coordinates.add(X.getEntry(2, 0));
        Log.d("Debug", "X:" + coordinates.get(0) + "Y:" + coordinates.get(1) + "Z:" + coordinates.get(2));

        return coordinates;
    }

    private class RttRangingResultCallback_Multi extends RangingResultCallback
    {

        private void requestNextFTMRanging()
        {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable()
                    {
                        @SuppressLint("MissingPermission")
                        @Override
                        public void run()
                        {
                            mWifiRttManager.startRanging(mRangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback_Multi);
                            ;
                        }
                    },
                    mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code)
        {
            Log.d("Debug", "onRangingFailure() code: " + code);
            text_output.setText("Ranging failed: onRangingFailure()");
            requestNextFTMRanging();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> results)
        {
            boolean flagAP1 = false;
            boolean flagAP2 = false;
            boolean flagAP3 = false;
            boolean flagAP4 = false;
            // 清楚上一次的测距结果
            clearTmpRangingResults();

            Log.d("Debug", "onRangingResults(): " + results);
            for (RangingResult result : results)
            {
                if (macAddress_1.equals(result.getMacAddress().toString()) & totalAP >= 1)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP1 = true;
                        Log.d("Debug", "Ranging Result AP1:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_1.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP1:" + result.getStatus());
                    }
                } else if (macAddress_2.equals(result.getMacAddress().toString()) & totalAP >= 2)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP2 = true;
                        Log.d("Debug", "Ranging Result AP2:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_2.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP2:" + result.getStatus());
                    }
                } else if (macAddress_3.equals(result.getMacAddress().toString()) & totalAP >= 3)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP3 = true;
                        Log.d("Debug", "Ranging Result AP3:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_3.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP3:" + result.getStatus());
                    }
                } else if (macAddress_4.equals(result.getMacAddress().toString()) & totalAP >= 4)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP4 = true;
                        Log.d("Debug", "Ranging Result AP4:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_4.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP4:" + result.getStatus());
                    }
                }
            }

            if (flagAP1 & flagAP2 & flagAP3 & flagAP4)
            {
                mFlagRangeSuccess = 2;
                Log.d("Debug", "mFlagRangeSuccess:" + mFlagRangeSuccess);
                text_output.setText("All AP Ranging success");
                // 增加测量成功改到测数
                mMultiRangeCount += 1;
                // 将测距结果加入到最终结果中
                rangingResults_1.add(tmpRangingResults_1.get(0));
                rangingResults_2.add(tmpRangingResults_2.get(0));
                rangingResults_3.add(tmpRangingResults_3.get(0));
                rangingResults_4.add(tmpRangingResults_4.get(0));
                Log.d("Debug", "Ranging success:");
                Log.d("Debug", "rangingResults_1: " + rangingResults_1.get(rangingResults_1.size() - 1).getDistanceMm() + "mm");
                Log.d("Debug", "rangingResults_2: " + rangingResults_2.get(rangingResults_2.size() - 1).getDistanceMm() + "mm");
                Log.d("Debug", "rangingResults_3: " + rangingResults_3.get(rangingResults_3.size() - 1).getDistanceMm() + "mm");
                Log.d("Debug", "rangingResults_4: " + rangingResults_4.get(rangingResults_4.size() - 1).getDistanceMm() + "mm");

                if (mMultiRangeCount <= MAX_MULTI_RANGE_COUNT)
                {
                    requestNextFTMRanging();
                } else
                {
                    // TODO: 把处理函数放在这里
                    //  如果要多次测量，就在这里加次数设定
                    calculateAndShowLocation();
                }

            } else if (flagAP1 | flagAP2 | flagAP3 | flagAP4)
            {
                mFlagRangeSuccess = 1;
                text_output.setText("Ranging failed, please retry");
                Log.d("Debug", "mFlagRangeSuccess:" + mFlagRangeSuccess);
            } else
            {
                mFlagRangeSuccess = -2;
                text_output.setText("Ranging failed, please retry");
                Log.d("Debug", "mFlagRangeSuccess:" + mFlagRangeSuccess);
            }
        }
    }

    private class RttRangingResultCallback extends RangingResultCallback
    {
        @Override
        public void onRangingFailure(int code)
        {
            Log.d("Debug", "onRangingFailure() code: " + code);
            text_output.setText("Ranging failed: onRangingFailure()");
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> results)
        {
            boolean flagAP1 = false;
            boolean flagAP2 = false;
            boolean flagAP3 = false;
            boolean flagAP4 = false;
            // 清楚上一次的测距结果
            clearTmpRangingResults();

            Log.d("Debug", "onRangingResults(): " + results);
            for (RangingResult result : results)
            {
                if (macAddress_1.equals(result.getMacAddress().toString()) & totalAP >= 1)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP1 = true;
                        Log.d("Debug", "Ranging Result AP1:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_1.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP1:" + result.getStatus());
                    }
                } else if (macAddress_2.equals(result.getMacAddress().toString()) & totalAP >= 2)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP2 = true;
                        Log.d("Debug", "Ranging Result AP2:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_2.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP2:" + result.getStatus());
                    }
                } else if (macAddress_3.equals(result.getMacAddress().toString()) & totalAP >= 3)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP3 = true;
                        Log.d("Debug", "Ranging Result AP3:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_3.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP3:" + result.getStatus());
                    }
                } else if (macAddress_4.equals(result.getMacAddress().toString()) & totalAP >= 4)
                {
                    // 检查测距结果的状态
                    if (result.getStatus() == RangingResult.STATUS_SUCCESS)
                    {
                        flagAP4 = true;
                        Log.d("Debug", "Ranging Result AP4:" + result.getDistanceMm() + "mm");
                        tmpRangingResults_4.add(result);
                    } else
                    {
                        Log.d("Debug", "Ranging failed AP4:" + result.getStatus());
                    }
                }
            }

            if (flagAP1 & flagAP2 & flagAP3 & flagAP4)
            {
                mFlagRangeSuccess = 2;
                Log.d("Debug", "mFlagRangeSuccess:" + mFlagRangeSuccess);
                text_output.setText("All AP Ranging success");
                // 测距成功，将测距结果加入到最终结果中
                rangingResults_1.add(tmpRangingResults_1.get(0));
                rangingResults_2.add(tmpRangingResults_2.get(0));
                rangingResults_3.add(tmpRangingResults_3.get(0));
                rangingResults_4.add(tmpRangingResults_4.get(0));
                Log.d("Debug", "Ranging success:");
                Log.d("Debug", "rangingResults_1: " + rangingResults_1.get(rangingResults_1.size() - 1).getDistanceMm() + "mm");
                Log.d("Debug", "rangingResults_2: " + rangingResults_2.get(rangingResults_2.size() - 1).getDistanceMm() + "mm");
                Log.d("Debug", "rangingResults_3: " + rangingResults_3.get(rangingResults_3.size() - 1).getDistanceMm() + "mm");
                Log.d("Debug", "rangingResults_4: " + rangingResults_4.get(rangingResults_4.size() - 1).getDistanceMm() + "mm");
                // TODO: 把处理函数放在这里
                //  如果要多次测量，就在这里加次数设定
                calculateAndShowLocation();

            } else if (flagAP1 | flagAP2 | flagAP3 | flagAP4)
            {
                mFlagRangeSuccess = 1;
                text_output.setText("Ranging failed, please retry");
                Log.d("Debug", "mFlagRangeSuccess:" + mFlagRangeSuccess);
            } else
            {
                mFlagRangeSuccess = -2;
                text_output.setText("Ranging failed, please retry");
                Log.d("Debug", "mFlagRangeSuccess:" + mFlagRangeSuccess);
            }
        }
    }

    public double[] getCoordinatesNN() throws IOException
    {
        //List<Double> coordinates = new ArrayList<Double>();
        //使用预训练的TensorFlow Lite模型计算坐标，需要四个AP测得的距离，rssi和四个AP的坐标
        //每个rangingResults是一个list，求测得的平均值
        double d1 = 0.0, d2 = 0.0, d3 = 0.0, d4 = 0.0;
        double rssi1 = 0.0, rssi2 = 0.0, rssi3 = 0.0, rssi4 = 0.0;
        for (int i = 1; i < rangingResults_1.size(); i++)
        {
            d1 += rangingResults_1.get(i).getDistanceMm() / 1000.0;
            rssi1 += rangingResults_1.get(i).getRssi();
        }
        for (int i = 0; i < rangingResults_2.size(); i++)
        {
            d2 += rangingResults_2.get(i).getDistanceMm() / 1000.0;
            rssi2 += rangingResults_2.get(i).getRssi();
        }
        for (int i = 0; i < rangingResults_3.size(); i++)
        {
            d3 += rangingResults_3.get(i).getDistanceMm() / 1000.0;
            rssi3 += rangingResults_3.get(i).getRssi();
        }
        for (int i = 0; i < rangingResults_4.size(); i++)
        {
            d4 += rangingResults_4.get(i).getDistanceMm() / 1000.0;
            rssi4 += rangingResults_4.get(i).getRssi();
        }
        d1 /= rangingResults_1.size();
        d2 /= rangingResults_2.size();
        d3 /= rangingResults_3.size();
        d4 /= rangingResults_4.size();
        rssi1 /= rangingResults_1.size();
        rssi2 /= rangingResults_2.size();
        rssi3 /= rangingResults_3.size();
        rssi4 /= rangingResults_4.size();

        //调用模型进行推断
        TFLiteModel tfliteModel = new TFLiteModel(context);
        double[] input = {d1, d2, d3, d4, rssi1, rssi2, rssi3, rssi4};
        double[] output = new double[3];

        output = tfliteModel.runInference(input);
        tfliteModel.tflite.close();
        return output;
    }
}