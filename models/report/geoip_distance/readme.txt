All images in this folder show the measured ping delay (ms) vs. the
distance between source and destination (in meters), computed from the
GeoIP data (latitude and longitude).

- geoip_dist_vs_ping_500.png is a scatter plot of 500 random data points;
- geoip_dist_vs_ping_all.png is a scatter plot of all data points;
- geoip_dist_vs_ping_all_heatmap.png is a heatmap-style plot of all data points.

There seem to be at least some relationship between distance and
delay, if only because the *minimal* ping time for a given distance
is correlated to the distance (there is nothing in the upper left
triangle). Among short pings (< 300 ms), there seems to be a linear
correlation, however it seems to disappear for longer pings (> 300 ms),
and even to be reversed.

There is nothing suggesting the GeoIP data is inaccurate, but the
distance seems to be a bad linear predictor for the ping delay.
