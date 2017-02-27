package de.sindzinski.wetter;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.HashSet;
import java.util.Locale;

import de.sindzinski.wetter.util.Utility;

/**
 * {@link ForecastAdapterDaily} exposes a list of weather forecasts
 * from a {@link Cursor} to a {@link android.widget.ListView}.
 */
public class ForecastAdapterDaily extends CursorAdapter {

    private static final int VIEW_TYPE_COUNT = 2;
    private static final int VIEW_TYPE_TODAY = 0;
    private static final int VIEW_TYPE_FUTURE_DAY = 1;

    // Flag to determine if we want to use a separate view for "today".
    private boolean mUseTodayLayout = true;
    int position;

    /**
     * Cache of the children views for a forecast list item.
     */
    public static class ViewHolder {
        public final ImageView mIconView;
        public final TextView mDateView;
        public final TextView mCityView;
        public final TextView mDescriptionView;
        public final TextView mMinMaxTempView;
        public final TextView mDayTempView;
        public final TextView mWindView;
        public final ImageView mIconCloudsView;
        public final TextView mCloudsView;
        public final TextView mConditionView;

        public ViewHolder(View view) {
            mCityView = (TextView) view.findViewById(R.id.list_item_city_textview);
            mIconView = (ImageView) view.findViewById(R.id.list_item_icon);
            mDateView = (TextView) view.findViewById(R.id.list_item_date_textview);
            mDescriptionView = (TextView) view.findViewById(R.id.list_item_forecast_textview);
            mDayTempView = (TextView) view.findViewById(R.id.list_item_day_textview);
            mMinMaxTempView = (TextView) view.findViewById(R.id.list_item_minmax_textview);
            mWindView = (TextView) view.findViewById(R.id.list_item_wind_textview);
            mIconCloudsView = (ImageView) view.findViewById(R.id.list_item_icon_clouds);
            mCloudsView = (TextView) view.findViewById(R.id.list_item_clouds_textview);
            mConditionView = (TextView) view.findViewById((R.id.list_item_condition_textview));
        }
    }

    public ForecastAdapterDaily(Context mContext, Cursor c, int flags) {
        super(mContext, c, flags);
    }

    @Override
    public View newView(Context mContext, Cursor cursor, ViewGroup parent) {
        // Choose the layout type
        int viewType = getItemViewType(cursor.getPosition());
        int layoutId = -1;
        switch (viewType) {
            case VIEW_TYPE_TODAY: {
                layoutId = R.layout.list_item_forecast_today_daily;
                break;
            }
            case VIEW_TYPE_FUTURE_DAY: {
                layoutId = R.layout.list_item_forecast_daily;
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
        // Read user preference for metric or imperial temperature units
        boolean isMetric = Utility.isMetric(mContext);

        // Get weather icon
        int weatherId = cursor.getInt(ForecastDailyFragment.COL_WEATHER_CONDITION_ID);
        int defaultImage;
        defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);

        String prefProvider = Utility.getProvider(mContext);
        String provider = mContext.getString(R.string.pref_provider_wug);

        String artPack = Utility.getWeatherArtPack(mContext);
        if (artPack.equals(mContext.getString(R.string.pref_art_pack_owm))) {
            String icon = cursor.getString(ForecastDailyFragment.COL_WEATHER_ICON);
            Glide.with(mContext)
                    .load(String.format(Locale.US, artPack, icon))
                    .error(defaultImage)
                    .crossFade()
                    .into(viewHolder.mIconView);
        } else if (artPack.equals(mContext.getString(R.string.pref_art_pack_cute_dogs))) {
            Glide.with(mContext)
                    .load(Utility.getArtUrlForWeatherCondition(mContext, weatherId))
                    .error(defaultImage)
                    .crossFade()
                    .into(viewHolder.mIconView);
        } else if (artPack.isEmpty()) {
            if (defaultImage>0) {
                // local images
                viewHolder.mIconView.setImageResource(defaultImage);
            }
        } else {
            String icon = cursor.getString(ForecastDailyFragment.COL_WEATHER_ICON);
            Glide.with(mContext)
                    .load(String.format(Locale.US, artPack, icon))
                    //.error(defaultImage)
                    .crossFade()
                    .into(viewHolder.mIconView);
        }

        // Read date from cursor
        long timeInMillis = cursor.getLong(ForecastDailyFragment.COL_WEATHER_DATE);
        Integer type = cursor.getInt(ForecastDailyFragment.COL_TYPE);

        // Find TextView and set formatted date on it
        viewHolder.mDateView.setText(Utility.getDailyDayString(mContext, timeInMillis));
        // Read weather forecast from cursor
        String description = cursor.getString(ForecastDailyFragment.COL_WEATHER_DESC);
        // Find TextView and set weather forecast on it
        viewHolder.mDescriptionView.setText(description);

        // For accessibility, add a content description to the icon field
        viewHolder.mIconView.setContentDescription(description);

        // Read wind speed and direction from cursor and update view
        float windSpeedStr = cursor.getFloat(ForecastDailyFragment.COL_WEATHER_WIND_SPEED);
        float windDirStr = cursor.getFloat(ForecastDailyFragment.COL_WEATHER_DEGREES);
        viewHolder.mWindView.setText(Utility.getSmallFormattedWind(mContext, windSpeedStr, windDirStr));

        int viewType = getItemViewType(cursor.getPosition());
        if (viewType == VIEW_TYPE_TODAY) {

            if ((Utility.getProvider(mContext).equals(mContext.getString(R.string.pref_provider_owm)))
                    && (this.position == cursor.getPosition())) {
                viewHolder.mConditionView.setVisibility(View.VISIBLE);
            } else {
                viewHolder.mConditionView.setVisibility(View.GONE);
            }

            String cityName = cursor.getString(ForecastDailyFragment.COL_CITY_NAME);
            viewHolder.mCityView.setText(cityName);
            // Read high temperature from cursor
            viewHolder.mDateView.setText(Utility.getDailyDayString(mContext, timeInMillis));
            double day = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_DAY_TEMP);
            viewHolder.mDayTempView.setText(Utility.formatTemperature(mContext, day, isMetric));
            double max = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_MAX_TEMP);
            double min = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_MIN_TEMP);
            viewHolder.mMinMaxTempView
                    .setText(Utility.formatTemperature(mContext, max, isMetric)
                            + " / " +
                            Utility.formatTemperature(mContext, min, isMetric));

            // Read day temperatures from cursor
            double morning = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_MORNING_TEMP);
            day = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_DAY_TEMP);
            double evening = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_EVENING_TEMP);
            double night = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_NIGHT_TEMP);
            viewHolder.mConditionView.setText(
                    Utility.formatTemperature(mContext, morning, isMetric) + " / " +
                            Utility.formatTemperature(mContext, day, isMetric) + " / " +
                            Utility.formatTemperature(mContext, evening, isMetric) + " / " +
                            Utility.formatTemperature(mContext, night, isMetric)
            );
        } else {

            if ((Utility.getProvider(mContext).equals(mContext.getString(R.string.pref_provider_owm))) &&
                    (this.position == cursor.getPosition())) {
                view.setBackgroundColor(ContextCompat.getColor(mContext, R.color.primary));
                viewHolder.mConditionView.setVisibility(View.VISIBLE);
                HashSet<TextView> textViews = Utility.getTextViews((ViewGroup) view);
                for (TextView tv : textViews) {
                    tv.setTextColor(ContextCompat.getColor(mContext, R.color.icons));
                }
            } else {
                view.setBackgroundColor(ContextCompat.getColor(mContext, R.color.background));
                HashSet<TextView> textViews = Utility.getTextViews((ViewGroup) view);
                for (TextView tv : textViews) {
                    tv.setTextColor(ContextCompat.getColor(mContext, R.color.primary_text));
                }
                viewHolder.mConditionView.setVisibility(View.GONE);
            }

            // Read day temperature from cursor
            double day = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_DAY_TEMP);
            viewHolder.mDayTempView.setText(Utility.formatTemperature(mContext, day, isMetric));
            // Read high low temperature from cursor
            double high = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_MAX_TEMP);
            double low = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_MIN_TEMP);
            viewHolder.mMinMaxTempView.setText(Utility.formatTemperature(mContext, high, isMetric) + " / " + Utility.formatTemperature(mContext, low, isMetric));
            double morning = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_MORNING_TEMP);
            double evening = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_EVENING_TEMP);
            double night = cursor.getDouble(ForecastDailyFragment.COL_WEATHER_NIGHT_TEMP);
            viewHolder.mConditionView.setText(
                    Utility.formatTemperature(mContext, morning, isMetric) + " / " +
                            Utility.formatTemperature(mContext, day, isMetric) + " / " +
                            Utility.formatTemperature(mContext, evening, isMetric) + " / " +
                            Utility.formatTemperature(mContext, night, isMetric)
            );

            int clouds = cursor.getInt(ForecastDailyFragment.COL_WEATHER_CLOUDS);
            if (clouds > 0) {
                int cloudsImage = Utility.getIconResourceForWeatherCondition(
                        802);
                viewHolder.mIconCloudsView.setImageResource(cloudsImage);
                viewHolder.mCloudsView.setText(Double.toString(clouds) + "%");
            }
        }
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
    }

    @Override
    public int getItemViewType(int position) {
//        return (position == this.position && mUseTodayLayout) ? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;
        return (position == 0 && mUseTodayLayout) ? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    public void selectedItem(int position) {
        this.position = position; //position must be a global variable
    }


}