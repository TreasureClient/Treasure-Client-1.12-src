# Friends & Messages: online server setup (Firebase, free)

Treasure Client's Friends tab needs one free Firebase project. This only has to be done once.

1. Go to https://console.firebase.google.com and sign in with your Google account.
2. **Create a project** named `treasure-client`. Turn **off** Google Analytics; it isn't needed.
3. In the left menu, open **Build > Authentication** and press **Get started**. On the **Sign-in method** tab, choose **Anonymous**, switch it on and press **Save**.
4. In the left menu, open **Build > Realtime Database** and press **Create database**. Pick the location closest to you, choose **Start in locked mode**, and press **Enable**.
5. Open the **Rules** tab. Delete everything in it, paste the whole content of `tcnet/database.rules.json`, and press **Publish**.
6. Copy two things:
   - **Database URL:** it's shown at the top of the **Data** tab and looks like `https://treasure-client-xxxx-default-rtdb.firebaseio.com`.
   - **Web API key:** press the gear icon, then **Project settings > General**. Under "Your apps", press the `</>` (Web) button, name the app `Treasure Client` (skip Hosting) and press **Register app**. Copy the `apiKey` value, which starts with `AIza`.

Then paste both into the chat. They go into `var TCNET = { apiKey: '', db: '' }` in the three `OfflineDownloadTemplate.txt` files.

The web API key is meant to be public (it's in every Firebase web app). The rules are what protect the data:
- Only accepted friends can message each other.
- Messages can't be edited by anyone else.
- Each player can only change their own profile.

## Where data lives
- `pub/<id>`: public card: name, lowercase name, number, 8x8 face, last seen.
- `ids/<number>`: who owns each number.
- `u/<id>`: private to that player:
  - `in`: friend requests received
  - `out`: requests sent
  - `fr`: friends
  - `nt`: time of the last message from each friend
  - `bl`: blocked players
  - `cfg`: settings
- `c/<idA>_<idB>`: messages between two friends (max 256 characters each).

To remove abuse, delete the player's `pub/<id>` and `u/<id>` in the **Data** tab.
