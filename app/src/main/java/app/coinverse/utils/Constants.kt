package app.coinverse.utils

object Constants {
    // Firebase
    const val RC_SIGN_IN = 123
    const val TIMESTAMP = "timestamp"
    const val QUALITY_SCORE = "qualityScore"

    // Home
    const val ON_BACK_PRESS_DELAY_IN_MILLIS = 500L
    const val SIGNIN_DIALOG_FRAGMENT_TAG = "signinDialogFragmentTag"

    // Content feed.
    const val CONTENT_KEY = "contentKey"
    const val CELL_CONTENT_MARGIN = 32
    const val PREFETCH_DISTANCE = 24
    const val PAGE_SIZE = 12
    const val YOUTUBE_DIALOG_FRAGMENT_TAG = "youtubeDialogFragmentTag"
    const val YOUTUBE_IS_PLAYING_KEY = "youtubeIsPlayingKey"
    const val YOUTUBE_CURRENT_TIME_KEY = "youtubeCurrentTimeKey"
    const val YOUTUBE_PORTRAIT_HEIGHT_DIVISOR = 2
    const val YOUTUBE_LANDSCAPE_WIDTH_DIVISOR = 1.2
    const val YOUTUBE_LANDSCAPE_HEIGHT_DIVISOR = 1.2
    const val CONTENT_FEED_VISIBILITY_DELAY = 300L

    // Analytics
    // Views
    const val YOUTUBE_VIEW = "YOUTUBE_VIEW"
    const val PROFILE_VIEW = "PROFILE_VIEW"
    // Events
    const val VIEW_CONTENT_EVENT = "view_content"
    const val START_CONTENT_EVENT = "start_content"
    const val CONSUME_CONTENT_EVENT = "consume_content"
    const val FINISH_CONTENT_EVENT = "finish_content"
    const val ORGANIZE_EVENT = "organize_content"
    const val SHARE_EVENT = "share_content"
    const val CLEAR_FEED_EVENT = "clear_feed"
    const val ARCHIVE_EVENT = "archive_content"
    const val CONSUME_THRESHOLD = 0.3333333333
    const val FINISH_THRESHOLD = 0.95
    // Params
    const val USER_ID_PARAM = "user_id"
    const val QUALITY_SCORE_PARAM = "quality_score"
    const val TIMESTAMP_PARAM = "timestamp"
    const val CREATOR_PARAM = "creator_name"
    const val FEED_TYPE_PARAM = "feed_type"
}