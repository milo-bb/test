import geocoder

# 緯度経度を指定
pos = (35.34029, 139.487999)
# OpenStreetMapを使って逆ジオコーディング
g = geocoder.osm(pos, method='reverse')

print('Country:', g.country)
print('State:', g.state)
print('City:', g.city)
print('Street:', g.street)

