package course_generator.weather;

import java.util.ArrayList;

import org.joda.time.DateTime;

import com.javadocmd.simplelatlng.LatLng;
import com.javadocmd.simplelatlng.LatLngTool;
import com.javadocmd.simplelatlng.util.LengthUnit;

import course_generator.CgData;
import course_generator.TrackData;
import course_generator.settings.CgSettings;

public class WeatherHistory {

	public NoaaDailyNormals dailyNormals;
	public ArrayList<NoaaDailyNormals> previousDailySummaries;
	public NoaaDailyNormals monthlyNormals;
	public NoaaWeatherStation noaaNormalsWeatherStation;
	public NoaaWeatherStation noaaSummariesWeatherStation;
	private CgSettings Settings;
	private TrackData Track;
	private LatLng searchAreaCenter;
	private double searchAreaRadius;


	public WeatherHistory(CgSettings settings) {
		Settings = settings;
	}


	public void RetrieveWeatherData(TrackData track) {
		if (track == null)
			return;

		Track = track;
		DateTime startTime = Track.data.get(0).getHour();

		determineWeatherSearchArea();

		NoaaWeatherHistoryRetriever weatherHistoryRetriever = NoaaWeatherHistoryRetriever
				.where(searchAreaCenter, searchAreaRadius).when(startTime).forUser(Settings.getNoaaToken()).build();

		noaaNormalsWeatherStation = weatherHistoryRetriever.getNoaaNormalsWeatherStation();
		noaaSummariesWeatherStation = weatherHistoryRetriever.getNoaaSummariesWeatherStation();
		dailyNormals = weatherHistoryRetriever.retrieveDailyNormals();
		previousDailySummaries = weatherHistoryRetriever.retrieveDailySummaries();
		monthlyNormals = weatherHistoryRetriever.retrieveMonthlyNormals();
		// PopulateFields(weatherHistoryContent);

		// MoonIllumination dd =
		// MoonIllumination.compute().on(startTime.toDate()).execute();
		// dd.getPhase();

		UpdateTrackWeatherData();

	}


	/**
	 * is to encompass most of the track to search a weather station as close as
	 * possible to the course
	 */
	private void determineWeatherSearchArea() {
		// Looking for the farthest point of the track
		double maxDistance = Double.MIN_VALUE;
		LatLng furthestPoint = null;
		LatLng startPoint = new LatLng(Track.data.get(0).getLatitude(), Track.data.get(0).getLongitude());
		for (CgData dataPoint : Track.data) {
			LatLng currentPoint = new LatLng(dataPoint.getLatitude(), dataPoint.getLongitude());

			double distanceFromStart = LatLngTool.distance(startPoint, currentPoint, LengthUnit.METER);

			if (distanceFromStart > maxDistance) {
				maxDistance = distanceFromStart;
				furthestPoint = currentPoint;
			}
		}

		// We find the center of the box formed by the starting point and
		// the farthest point

		double distanceFromStart = LatLngTool.distance(startPoint, furthestPoint, LengthUnit.METER);
		double bearingBetweenPoint = LatLngTool.initialBearing(startPoint, furthestPoint);

		searchAreaCenter = LatLngTool.travel(startPoint, bearingBetweenPoint, distanceFromStart / 2, LengthUnit.METER);
		searchAreaRadius = distanceFromStart / 2;

	}


	private void UpdateTrackWeatherData() {
		Track.setHistoricalWeather(this);
	}

}
