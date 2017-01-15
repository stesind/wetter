package de.sindzinski.wetter;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import static de.sindzinski.wetter.data.WeatherContract.TYPE_HOURLY;

/**
 * {@link ForecastAdapterHourly} exposes a list of weather forecasts
 * from a {@link Cursor} to a {@link android.widget.ListView}.
 */
public class ForecastAdapterHourly extends CursorAdapter {

    private static final int VIEW_TYPE_COUNT = 2;
    private static final int VIEW_TYPE_TODAY = 0;
    private static final int VIEW_TYPE_FUTURE_DAY = 1;

    // Flag to determine if we want to use a separate view for "today".
    private boolean mUseTodayLayout = true;

    /**
     * Cache of the children views for a forecast list item.
     */
    public static class ViewHolder {
        public final ImageView mIconView;
        public final ImageView mIconCondView;

        public final TextView mDateView;
        public final TextView mCityView;
        public final TextView mDescriptionView;
        public final TextView mTempView;
        public final TextView mCondView;
        public final TextView mWindView;

        public ViewHolder(View view) {
            mCityView = (TextView) view.findViewById(R.id.list_item_city_textview);
            mIconView = (ImageView) view.findViewById(R.id.list_item_icon);
            mDateView = (TextView) view.findViewById(R.id.list_item_date_textview);
            mDescriptionView = (TextView) view.findViewById(R.id.list_item_forecast_textview);
            mTempView = (TextView) view.findViewById(R.id.list_item_temp_textview);
            mCondView = (TextView) view.findViewById(R.id.list_item_cond_textview);
            mWindView = (TextView) view.findViewById(R.id.list_item_wind_textview);
            mIconCondView = (ImageView) view.findViewById(R.id.list_item_icon_cond);

        }
    }

    public ForecastAdapterHourly(Context mContext, Cursor c, int flags) {
        super(mContext, c, flags);
    }

    @Override
    public View newView(Context mContext, Cursor cursor, ViewGroup parent) {
        // Choose the layout type
        int viewType = getItemViewType(cursor.getPosition());
        int layoutId = -1;
        switch (viewType) {
            case VIEW_TYPE_TODAY: {
                layoutId = R.layout.list_item_forecast_today_hourly;
                break;
            }
            case VIEW_TYPE_FUTURE_DAY: {
                layoutId = R.layout.list_item_forecast_hourly;
                break;
            }
        }

        View view = LayoutInflater.from(mContext).inflate(layoutId, parent, false);

        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);

        return view;
    }

    @Override
    public void bindView(View view, Context mContext, Cursor cursor) {

        ViewHolder viewHolder = (ViewHolder) view.getTag();

        int viewType = getItemViewType(cursor.getPosition());
        int weatherId = cursor.getInt(ForecastHourlyFragment.COL_WEATHER_CONDITION_ID);
        int defaultImage;
        switch (viewType) {
            case VIEW_TYPE_TODAY: {
                // Get weather icon
//                viewHolder.mIconView.setImageResource(Utility.getArtResourceForWeatherCondition(
//                        cursor.getInt(ForecastHourlyFragment.COL_WEATHER_CONDITION_ID)));
                defaultImage = Utility.getArtResourceForWeatherCondition(weatherId);
                viewHolder.mCityView.setText(cursor.getString(ForecastHourlyFragment.COL_CITY_NAME));
                break;
            }
            default: {
                // Get weather icon
                defaultImage = Utility.getIconResourceForWeatherCondition(
                        weatherId);
                viewHolder.mIconView.setImageResource(Utility.getIconResourceForWeatherCondition(
                        cursor.getInt(ForecastHourlyFragment.COL_WEATHER_CONDITION_ID)));
                break;
            }
        }

        if (Utility.usingLocalGraphics(mContext)) {
            viewHolder.mIconView.setImageResource(defaultImage);
        } else {
            Glide.with(mContext)
                    .load(Utility.getArtUrlForWeatherCondition(mContext, weatherId))
                    .error(defaultImage)
                    .crossFade()
                    .into(viewHolder.mIconView);
        }
        // Read date from cursor
        long timeInMillis = cursor.getLong(ForecastHourlyFragment.COL_WEATHER_DATE);
        Integer type = cursor.getInt(ForecastHourlyFragment.COL_TYPE);

        // Find TextView and set formatted date on it
            viewHolder.mDateView.setText(Utility.getHourlyDayString(mContext, timeInMillis));

        // Read weather forecast from cursor
        String description = cursor.getString(ForecastHourlyFragment.COL_WEATHER_DESC);
        // Find TextView and set weather forecast on it
        viewHolder.mDescriptionView.setText(description);

        // For accessibility, add a content description to the icon field
        viewHolder.mIconView.setContentDescription(description);

        // Read user preference for metric or imperial temperature units
        boolean isMetric = Utility.isMetric(mContext);

        if (viewType == VIEW_TYPE_TODAY) {
            // Read high temperature from cursor
            double temp = cursor.getDouble(ForecastHourlyFragment.COL_WEATHER_TEMP);
            viewHolder.mTempView
                    .setText(Utility.formatTemperature(mContext, temp, isMetric));

            // Read day temperatures from cursor
            double rain = cursor.getDouble(ForecastHourlyFragment.COL_WEATHER_RAIN);
            double snow = cursor.getDouble(ForecastHourlyFragment.COL_WEATHER_SNOW);
            int clouds = cursor.getInt(ForecastHourlyFragment.COL_WEATHER_CLOUDS);
            if ((rain+snow)> 0) {
                viewHolder.mCondView.setText(
                        (double) Math.round((rain) * 100) / 100 +
                                " / " +
                                (double) Math.round((snow) * 100) / 100);
            }
        } else {
            // Read high temperature from cursor
            double temp = cursor.getDouble(ForecastHourlyFragment.COL_WEATHER_TEMP);
            viewHolder.mTempView.setText(Utility.formatTemperature(mContext, temp, isMetric));

            double rain = cursor.getDouble(ForecastHourlyFragment.COL_WEATHER_RAIN);
            double snow = cursor.getDouble(ForecastHourlyFragment.COL_WEATHER_SNOW);
            double rainSnow = (double) Math.round((rain+snow) * 100) / 100;
            if ((rainSnow)>0) {
                viewHolder.mIconCondView.setImageResource(defaultImage);
                viewHolder.mCondView.setText(Double.toString(rainSnow)+"mm");
            }
        }
        // Read wind speed and direction from cursor and update view
        float windSpeedStr = cursor.getFloat(ForecastHourlyFragment.COL_WEATHER_WIND_SPEED);
        float windDirStr = cursor.getFloat(ForecastHourlyFragment.COL_WEATHER_DEGREES);
        viewHolder.mWindView.setText(Utility.getSmallFormattedWind(mContext, windSpeedStr, windDirStr));
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0 && mUseTodayLayout) ? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }
}