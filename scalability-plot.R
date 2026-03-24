library(tidyverse)
library(showtext)
library(hrbrthemes)
library(scales)

# Load fonts
font_add_google("Roboto Condensed")
font_add_google("Roboto Mono")
showtext_auto()

# Load data
data <- read_csv("build/reports/scalability-evaluation.csv")

ggplot(data, aes(x = NumTasks, y = TotalLatencyMean)) +
  # Use geom_ribbon instead of geom_errorbar
  geom_ribbon(aes(ymin = pmax(0.1, TotalLatencyMean - TotalLatencySD), 
                  ymax = TotalLatencyMean + TotalLatencySD), 
              fill = "#66C2A5", alpha = 0.15, color = NA) +
  geom_line(linewidth = 0.8, color = "#66C2A5") +
  geom_point(size = 3, color = "#66C2A5") +
  scale_x_continuous(breaks = data$NumTasks) +
  scale_y_continuous(labels = label_number(suffix = "ms", big.mark = ",")) +
  labs(
    x = "Number of Process Tasks",
    y = "Total Pipeline Latency"
  ) +
  theme_ipsum_rc() + 
  theme(
    legend.position = "bottom",
    panel.grid.minor.y = element_blank(),
    axis.text.x = element_text(color = "gray60", family = "Roboto Mono"),
    axis.text.y = element_text(color = "gray60", family = "Roboto Mono"),
    panel.grid.major = element_line(color = "gray90", linewidth = 0.5),
    plot.title = element_text(hjust = 0.5),
    plot.margin = margin(0, 0, 0, 0)
  )
