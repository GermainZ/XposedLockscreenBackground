/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.germainz.xlb;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewManager;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModLockscreen {
    private static final String TAG = "GB:ModLockscreen";
    private static final String CLASS_KGVIEW_MANAGER = "com.android.internal.policy.impl.keyguard.KeyguardViewManager";
    private static final boolean DEBUG = false;

    private static XSharedPreferences mPrefs;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote(final XSharedPreferences prefs) {
        try {
            mPrefs = prefs;
            final Class<?> kgViewManagerClass = XposedHelpers.findClass(CLASS_KGVIEW_MANAGER, null);

            XposedHelpers.findAndHookMethod(kgViewManagerClass, "maybeCreateKeyguardLocked",
                    boolean.class, boolean.class, Bundle.class, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mPrefs.reload();
                    ViewManager viewManager = (ViewManager) XposedHelpers.getObjectField(
                            param.thisObject, "mViewManager");
                    FrameLayout keyGuardHost = (FrameLayout) XposedHelpers.getObjectField(
                            param.thisObject, "mKeyguardHost");
                    WindowManager.LayoutParams windowLayoutParams = (WindowManager.LayoutParams) 
                            XposedHelpers.getObjectField(param.thisObject, "mWindowLayoutParams");

                    final String bgType = prefs.getString(
                            XlbSettings.PREF_KEY_LOCKSCREEN_BACKGROUND,
                            XlbSettings.LOCKSCREEN_BG_DEFAULT);

                    if (!bgType.equals(XlbSettings.LOCKSCREEN_BG_DEFAULT)) {
                        windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                    } else {
                        windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                    }
                    viewManager.updateViewLayout(keyGuardHost, windowLayoutParams);
                    if (DEBUG) log("maybeCreateKeyguardLocked: layout updated");
                }
            });

            XposedHelpers.findAndHookMethod(kgViewManagerClass, "inflateKeyguardView",
                    Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mPrefs.reload();

                    FrameLayout keyguardView = (FrameLayout) XposedHelpers.getObjectField(
                            param.thisObject, "mKeyguardView");

                    final String bgType = mPrefs.getString(
                            XlbSettings.PREF_KEY_LOCKSCREEN_BACKGROUND, 
                            XlbSettings.LOCKSCREEN_BG_DEFAULT);

                    if (bgType.equals(XlbSettings.LOCKSCREEN_BG_COLOR)) {
                        int color = mPrefs.getInt(
                                XlbSettings.PREF_KEY_LOCKSCREEN_BACKGROUND_COLOR, Color.BLACK);
                        keyguardView.setBackgroundColor(color);
                        if (DEBUG) log("inflateKeyguardView: background color set");
                    } else if (bgType.equals(XlbSettings.LOCKSCREEN_BG_IMAGE)) {
                        try {
                            Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            FrameLayout flayout = new FrameLayout(context);
                            flayout.setLayoutParams(new LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, 
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                            Context gbContext = context.createPackageContext(
                                    Xlb.PACKAGE_NAME, 0);
                            String wallpaperFile = gbContext.getFilesDir() + "/lockwallpaper";
                            Bitmap background = BitmapFactory.decodeFile(wallpaperFile);
                            Drawable d = new BitmapDrawable(context.getResources(), background);
                            ImageView mLockScreenWallpaperImage = new ImageView(context);
                            mLockScreenWallpaperImage.setScaleType(ScaleType.CENTER_CROP);
                            mLockScreenWallpaperImage.setImageDrawable(d);
                            flayout.addView(mLockScreenWallpaperImage, -1, -1);
                            keyguardView.addView(flayout,0);
                            if (DEBUG) log("inflateKeyguardView: background image set");
                        } catch (NameNotFoundException e) {
                            XposedBridge.log(e);
                        }
                    }
                }
            });

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

}
