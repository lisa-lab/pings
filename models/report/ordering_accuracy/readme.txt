Images in that folder report the ordering accuracy (see eq. 10 in the
report), restricted to pairs of samples (j, j') where the difference
between the measured delays, |t_j - t_j'|, is above some threshold.
That threshold is the x axis of the plots.

For each value x of the threshold, the ordering accuracy is estimated
by sampling 500,000 pairs (randomly and independently), and keeping
only the ones for which |t_j - t_j'| > x. That means the pairs with
similar latency are ignored in the computation of the mean: they are not
considered correct nor incorrect.

Plots are as follow:
- geoip_model.png: The model uses only the GeoIP distance
- country_model.png: The model uses the (country1, country2) information
- full_model.png: The model described in the report

The full model becomes better at predicting the correct ranking when
the closer example pairs (which should be harder to predict for a
good model) are removed.
