/*
 * Course Generator
 * Copyright (C) 2016 Pierre Delore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package course_generator.weather;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ResourceBundle;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import course_generator.TrackData;
import course_generator.settings.CgSettings;
import course_generator.utils.CgConst;
import course_generator.utils.CgLog;
import course_generator.utils.Utils;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;

public class JPanelWeather extends JFXPanel implements PropertyChangeListener {
	private static final long serialVersionUID = -7168142806619093218L;
	private ResourceBundle bundle;
	private CgSettings settings = null;
	private JToolBar toolBar;
	private JButton btWeatherDataSave;
	private JButton btWeatherRefresh;
	private JLabel lbInformation;
	private JLabel InformationWarning;
	private JLabel getNoaaTokenLink;
	private TrackData track = null;
	private WebView titi;
	private String weatherDataSheetContent;


	public JPanelWeather(CgSettings settings) {
		super();
		this.settings = settings;
		this.settings.addNoaaTokenChangeListener(this);
		bundle = java.util.ResourceBundle.getBundle("course_generator/Bundle");
		initComponents();
	}


	private void initComponents() {
		setLayout(new java.awt.BorderLayout());

		// -- Statistic tool bar
		// ---------------------------------------------------
		createWeatherToolbar();
		UpdatePanel();
		add(toolBar, java.awt.BorderLayout.NORTH);

		final JFXPanel fxPanel = new JFXPanel();

		// Creation of scene and future interactions with JFXPanel
		// should take place on the JavaFX Application Thread
		Platform.runLater(() -> {
			titi = new WebView();
			fxPanel.setScene(new Scene(titi));
		});
		add(fxPanel, java.awt.BorderLayout.CENTER);
	}


	/**
	 * Create the weather toolbar
	 */
	private void createWeatherToolbar() {
		toolBar = new javax.swing.JToolBar();
		toolBar.setOrientation(javax.swing.SwingConstants.HORIZONTAL);
		toolBar.setFloatable(false);
		toolBar.setRollover(true);
		toolBar.setAlignmentX(Component.LEFT_ALIGNMENT);

		// -- Save
		// --------------------------------------------------------------
		btWeatherDataSave = new javax.swing.JButton();
		btWeatherDataSave.setIcon(Utils.getIcon(this, "save_html.png", settings.ToolbarIconSize));
		btWeatherDataSave.setToolTipText(bundle.getString("JPanelWeather.btWeatherDataSave.toolTipText"));
		btWeatherDataSave.setEnabled(false);
		btWeatherDataSave.setFocusable(false);
		btWeatherDataSave.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				SaveStat();
			}
		});
		toolBar.add(btWeatherDataSave);

		// -- Separator
		// ---------------------------------------------------------
		toolBar.add(new javax.swing.JToolBar.Separator());

		// -- Refresh
		// --------------------------------------------------------------
		btWeatherRefresh = new javax.swing.JButton();
		btWeatherRefresh.setIcon(Utils.getIcon(this, "refresh.png", settings.ToolbarIconSize));
		btWeatherRefresh.setToolTipText(bundle.getString("JPanelWeather.btWeatherRefresh.toolTipText"));
		btWeatherRefresh.setEnabled(false);
		btWeatherRefresh.setFocusable(false);
		btWeatherRefresh.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				refresh(track, true);
			}
		});
		toolBar.add(btWeatherRefresh);

		// -- Tab information
		// --------------------------------------------------------------
		InformationWarning = new JLabel(Utils.getIcon(this, "cancel.png", settings.ToolbarIconSize));
		InformationWarning.setVisible(false);
		InformationWarning.setFocusable(false);
		toolBar.add(InformationWarning);

		lbInformation = new JLabel(" " + bundle.getString("JPanelWeather.lbInformationMissingNoaaToken.Text"));
		Font boldFont = new Font(lbInformation.getFont().getName(), Font.BOLD, lbInformation.getFont().getSize());
		lbInformation.setFont(boldFont);
		InformationWarning.setFocusable(false);
		lbInformation.setVisible(false);
		toolBar.add(lbInformation);

		getNoaaTokenLink = new JLabel(". " + bundle.getString("JPanelWeather.lbNOAARequestTokenLink.Text"));
		getNoaaTokenLink.setForeground(Color.BLUE.darker());
		boldFont = new Font(getNoaaTokenLink.getFont().getName(), Font.BOLD, getNoaaTokenLink.getFont().getSize());
		getNoaaTokenLink.setFont(boldFont);
		getNoaaTokenLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		getNoaaTokenLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					Desktop.getDesktop().browse(new URI("https://www.ncdc.noaa.gov/cdo-web/token"));

				} catch (IOException | URISyntaxException e1) {
					e1.printStackTrace();
				}
			}
		});
		getNoaaTokenLink.setVisible(false);
		toolBar.add(getNoaaTokenLink);

		refresh(null, false);
	}


	private void UpdatePanel() {

		boolean isNoaaTokenValid = settings.isNoaaTokenValid();

		InformationWarning.setVisible(!isNoaaTokenValid);
		lbInformation.setVisible(!isNoaaTokenValid);
		getNoaaTokenLink.setVisible(!isNoaaTokenValid);

		btWeatherRefresh.setEnabled(isNoaaTokenValid && track != null);
	}


	/**
	 * Refreshes the weather tab
	 * 
	 * @param track
	 *            The current track
	 * @param retrieveOnlineData
	 *            True if we need to retrieve data from the weather provider,
	 *            otherwise, we retrieve it from the track.
	 */
	public void refresh(TrackData track, boolean retrieveOnlineData) {
		if (track == null || track.data.isEmpty()) {
			UpdatePanel();
			return;
		}

		this.track = track;

		UpdatePanel();

		HistoricalWeather previousWeatherData = null;
		if (retrieveOnlineData) {
			if (!Utils.isInternetReachable()) {
				lbInformation.setText(bundle.getString("JPanelWeather.lbInformationMissingInternetConnection.Text"));
				lbInformation.setVisible(true);
				InformationWarning.setVisible(true);
				return;
			}

			previousWeatherData = new HistoricalWeather(settings);

			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			previousWeatherData.RetrieveWeatherData(track);
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

		} else {
			// If exists, get the historical weather from the CGX course
			previousWeatherData = track.getHistoricalWeather();
		}

		if (previousWeatherData == null) {
			updateDataSheet("");
			btWeatherDataSave.setEnabled(false);
			return;
		}

		btWeatherDataSave.setEnabled(true);

		String newContent = PopulateWeatherDataSheet(previousWeatherData);

		updateDataSheet(newContent);

	}


	/**
	 * Refresh the view and set the cursor position
	 * 
	 */
	private void updateDataSheet(String dataSheetContent) {
		weatherDataSheetContent = dataSheetContent;
		Platform.runLater(() -> {
			titi.getEngine().loadContent(weatherDataSheetContent);
		});
	}


	public void propertyChange(PropertyChangeEvent evt) {
		if (!evt.getPropertyName().equals("NoaaTokenChanged"))
			return;
		UpdatePanel();
	}


	private String PopulateWeatherDataSheet(HistoricalWeather previousWeatherData) {

		StringBuilder sheetSkeleton = new StringBuilder();
		InputStream is = getClass().getResourceAsStream("weatherdatasheet.html");

		try {
			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			BufferedReader br = new BufferedReader(isr);

			String line;
			while ((line = br.readLine()) != null) {
				sheetSkeleton.append(line);
			}
			br.close();
			isr.close();
			is.close();
		} catch (IOException e) {
			CgLog.error("RefreshStat : Impossible to read the template file from resource");
			e.printStackTrace();
		}

		// EVENT SUMMARY titles
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@100", bundle.getString("JPanelWeather.EventSummary.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@101", bundle.getString("JPanelWeather.Date.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@102", bundle.getString("JPanelWeather.SunriseSunset.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@103", bundle.getString("JPanelWeather.DaylightHours.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@104", bundle.getString("JPanelWeather.MoonPhase.Text"));

		// HISTORICAL WEATHER DATA titles
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@200",
				bundle.getString("JPanelWeather.HistoricalWeatherData.Text"));
		if (previousWeatherData.pastDailySummaries != null && !previousWeatherData.pastDailySummaries.isEmpty()) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@201",
					previousWeatherData.pastDailySummaries.get(0).getDate().toString("EE yyyy-MM-dd"));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@202",
					previousWeatherData.pastDailySummaries.get(1).getDate().toString("EE yyyy-MM-dd"));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@203",
					previousWeatherData.pastDailySummaries.get(2).getDate().toString("EE yyyy-MM-dd"));
		}
		if (previousWeatherData.normalsDaily != null) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@204", bundle.getString("JPanelWeather.NormalsDaily.Text"));
		} else {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@204", "");

		}
		if (previousWeatherData.normalsMonthly != null) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@205",
					bundle.getString("JPanelWeather.NormalsMonthly.Text"));
		} else {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@205", "");
		}

		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@206", bundle.getString("JPanelWeather.MaxTemperature.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@207", bundle.getString("JPanelWeather.AvgTemperature.Text"));
		// TODO SWAP ????
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@208", bundle.getString("JPanelWeather.MinTemperature.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@209", bundle.getString("JPanelWeather.Precipitation.Text"));
		// TODO OTHER DATA ?????
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@211", bundle.getString("JPanelWeather.StationName.Text"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@212",
				bundle.getString("JPanelWeather.DistanceFromStart.Text"));

		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@110",
				track.data.get(0).getHour().toString("yyyy-MM-dd HH:mm"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@111", track.EndNightTime.toString("HH:mm"));
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@112", track.StartNightTime.toString("HH:mm"));

		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@113", previousWeatherData.daylightHours);
		sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@114", previousWeatherData.getMoonPhaseDescription());

		if (previousWeatherData.pastDailySummaries != null && !previousWeatherData.pastDailySummaries.isEmpty()) {
			// Year -1
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@220",
					displayTemperature(previousWeatherData.pastDailySummaries.get(0).getTemperatureMax()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@225",
					displayTemperature(previousWeatherData.pastDailySummaries.get(0).getTemperatureAverage()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@230",
					displayTemperature(previousWeatherData.pastDailySummaries.get(0).getTemperatureMin()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@235",
					previousWeatherData.pastDailySummaries.get(0).getPrecipitation());
			// Year -2
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@221",
					displayTemperature(previousWeatherData.pastDailySummaries.get(1).getTemperatureMax()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@226",
					displayTemperature(previousWeatherData.pastDailySummaries.get(1).getTemperatureAverage()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@231",
					displayTemperature(previousWeatherData.pastDailySummaries.get(1).getTemperatureMin()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@236",
					previousWeatherData.pastDailySummaries.get(1).getPrecipitation());

			// Year -3
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@222",
					displayTemperature(previousWeatherData.pastDailySummaries.get(2).getTemperatureMax()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@227",
					displayTemperature(previousWeatherData.pastDailySummaries.get(2).getTemperatureAverage()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@232",
					displayTemperature(previousWeatherData.pastDailySummaries.get(2).getTemperatureMin()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@237",
					previousWeatherData.pastDailySummaries.get(2).getPrecipitation());
		}

		// Daily normals
		if (previousWeatherData.normalsDaily != null) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@223",
					displayTemperature(previousWeatherData.normalsDaily.getTemperatureMax()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@228",
					displayTemperature(previousWeatherData.normalsDaily.getTemperatureAverage()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@233",
					displayTemperature(previousWeatherData.normalsDaily.getTemperatureMin()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@238", previousWeatherData.normalsDaily.getPrecipitation());
		} else {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@223", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@228", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@233", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@238", "");
		}

		// Monthly normals
		if (previousWeatherData.normalsMonthly != null) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@224",
					displayTemperature(previousWeatherData.normalsMonthly.getTemperatureMax()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@229",
					displayTemperature(previousWeatherData.normalsMonthly.getTemperatureAverage()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@234",
					displayTemperature(previousWeatherData.normalsMonthly.getTemperatureMin()));
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@239",
					displayTemperature(previousWeatherData.normalsMonthly.getPrecipitation()));
		} else {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@224", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@229", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@234", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@239", "");
		}

		if (previousWeatherData.noaaSummariesWeatherStation != null) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@244",
					previousWeatherData.noaaSummariesWeatherStation.getName());

			double distanceFromStart = previousWeatherData.noaaSummariesWeatherStation.getDistanceFromStart();
			String distance = "";
			if (settings.Unit == CgConst.UNIT_MILES_FEET)
				distanceFromStart = Utils.Km2Miles(distanceFromStart);

			distance = String.format("%.0f", distanceFromStart);
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@246", distance + " " + Utils.uLDist2String(settings.Unit));
		} else {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@244", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@246", "");
		}
		if (previousWeatherData.noaaNormalsWeatherStation != null) {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@245",
					previousWeatherData.noaaNormalsWeatherStation.getName());

			double distanceFromStart = previousWeatherData.noaaNormalsWeatherStation.getDistanceFromStart();
			String distance = "";
			if (settings.Unit == CgConst.UNIT_MILES_FEET)
				distanceFromStart = Utils.Meter2uMiles(distanceFromStart) / 1000.0;

			distance = String.format("%.0f", distanceFromStart);
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@247", distance + " " + Utils.uLDist2String(settings.Unit));
		} else {
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@245", "");
			sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@247", "");
		}

		// sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@245",
		// "<img
		// SRC=\"data:image/png;base64,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\">");
		// sheetSkeleton = Utils.sbReplace(sheetSkeleton, "@247",
		// "<img
		// SRC=\"data:image/png;base64,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\">");
		// CgLog.info(sheetSkeleton.toString());
		return ReplaceImages(sheetSkeleton.toString(), previousWeatherData.moonFraction);
	}


	/**
	 * Save the statistics in TXT format
	 */
	private void SaveStat() {
		String s;
		s = Utils.SaveDialog(this, settings.LastDir, "", ".html", bundle.getString("frmMain.HTMLFile"), true,
				bundle.getString("frmMain.FileExist"));

		if (!s.isEmpty()) {
			try {
				FileWriter out = new FileWriter(s);

				out.write(weatherDataSheetContent);
				out.close();
			} catch (Exception f) {
				CgLog.error("SaveStat : impossible to save the statistic file");
				f.printStackTrace();
			}
			// -- Store the directory
			settings.LastDir = Utils.GetDirFromFilename(s);
		}
	}


	/**
	 * Creates a String containing a temperature value.
	 * 
	 * @param temperatureValue
	 *            The temperature value
	 * @return A String containing a temperature information
	 */
	private String displayTemperature(String temperatureValue) {
		if (temperatureValue == null || temperatureValue == "")
			return "";

		return Utils.FormatTemperature(Double.valueOf(temperatureValue), settings.Unit)
				+ Utils.uTemperatureToString(settings.Unit);
	}


	/**
	 * Because the image paths in the original HTML reference images in the Course
	 * Generator jar (i.e: not accessible by any browser), we convert all the images
	 * references to their actual Base64 value. Why we can't use the Base64
	 * representation in Course Generator : Because Swing's (default) HTML support
	 * does not extend to Base 64 encoded images.
	 * 
	 * @param originalText
	 *            The original HTML page
	 * @return The HTML page containing base64 representations of each image.
	 */
	private String ReplaceImages(String originalText, double moonFraction) {
		Document document = Jsoup.parse(originalText);

		document.select("img[src]").forEach(e -> {

			String image = e.attr("src");
			String base64 = "";
			switch (image) {
			case "sunrise":
				base64 = Utils.imageToBase64(this, "sunrise.png", 128);
				break;
			case "sunset":
				base64 = Utils.imageToBase64(this, "sunset.png", 128);
				break;
			case "thermometer":
				base64 = Utils.imageToBase64(this, "thermometer.png", 128);
				break;
			case "moonphase":
				String moonPhaseIcon = HistoricalWeather.getMoonPhaseIcon(moonFraction);
				base64 = Utils.imageToBase64(this, moonPhaseIcon, 128);
				break;
			}

			e.attr("src", "data:image/png;base64," + base64);

		});

		return document.toString();
	}
}
