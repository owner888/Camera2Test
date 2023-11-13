package jp.co.cyberagent.android.gpuimage.sample;


import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDexApplication;

/**
 * Created 2023/11/12 15:04
 * Author:charcolee
 * Version:V1.0
 * ----------------------------------------------------
 * 文件描述：
 * ----------------------------------------------------
 */
public class MyApplication extends MultiDexApplication {

   @Override
   protected void attachBaseContext(Context base) {
      super.attachBaseContext(base);
   }

}
