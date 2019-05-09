import matplotlib.pyplot as plt
import math
import numpy as np

x = np.array(['1/28週', '2/5週', '2/12週', '2/18週', '2/25週', '3/4週', '3/11週', '3/18週', '3/26週', '4/4週', '4/11週',
              '4/18週', '4/24週', '5/9週'])
x_position = np.arange(len(x))

y_1 = np.array([5, 13, 5, 8, 7, 19, 16, 16, 14, 19, 8, 19, 24, 0])
y_2 = np.array([5, 13, 5, 8, 7, 19, 16, 16, 14, 19, 8, 19, 24, 0])
y_3 = np.array([5, 12, 5, 1, 14, 0, 0, 11, 0, 0, 0, 19, 15, 7])
y_4 = np.array([0, 1, 1, 7, 1, 20, 36, 5, 14, 19, 7, 0, 9, 2])
y_5 = np.array([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])

fig = plt.figure()
ax = fig.add_subplot(1, 1, 1)
ax.bar(x_position, y_1, width=0.2, label='依頼数')
ax.bar(x_position + 0.2, y_2, width=0.2, label='受付数')
ax.bar(x_position + 0.4, y_3, width=0.2, label='回答数')
ax.bar(x_position + 0.6, y_4, width=0.2, label='対応中')
ax.bar(x_position + 0.8, y_5, width=0.2, label='調整中')
ax.legend()
ax.set_xticks(x_position + 0.3)
ax.set_xticklabels(x)
plt.show()


