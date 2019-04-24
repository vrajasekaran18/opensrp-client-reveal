package org.smartregister.reveal.util;

import android.content.Context;
import android.support.v7.app.AlertDialog;

import org.smartregister.reveal.R;

/**
 * Created by samuelgithengi on 3/22/19.
 */
public class AlertDialogUtils {


    public static void displayNotification(Context context, int title, int message, Object... formatArgs) {
        if (formatArgs.length == 0)
            new AlertDialog.Builder(context).setMessage(message).setTitle(title).setPositiveButton(R.string.ok, null).show();
        else
            new AlertDialog.Builder(context).setMessage(context.getString(message, formatArgs)).setTitle(title).setPositiveButton(R.string.ok, null).show();
    }


    public static void displayNotification(Context context, String message) {
        new AlertDialog.Builder(context).setMessage(message).setTitle(R.string.fetch_structures_title).setPositiveButton(R.string.ok, null).show();
    }
}
