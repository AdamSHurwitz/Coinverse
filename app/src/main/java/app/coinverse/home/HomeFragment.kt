package app.coinverse.home

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.Intent.*
import android.location.Location
import android.net.Uri
import android.os.Build.BRAND
import android.os.Build.MODEL
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import app.coinverse.BuildConfig.VERSION_NAME
import app.coinverse.Enums.AccountType.READ
import app.coinverse.Enums.FeedType.MAIN
import app.coinverse.Enums.FeedType.SAVED
import app.coinverse.Enums.PaymentStatus.FREE
import app.coinverse.Enums.SignInType.DIALOG
import app.coinverse.Enums.SignInType.FULLSCREEN
import app.coinverse.R
import app.coinverse.R.drawable.ic_astronaut_color_accent_24dp
import app.coinverse.R.string.*
import app.coinverse.content.ContentDialogFragment
import app.coinverse.content.ContentFragment
import app.coinverse.databinding.FragmentHomeBinding
import app.coinverse.firebase.FirestoreCollections.usersCollection
import app.coinverse.home.HomeFragmentDirections.actionHomeFragmentToProfileFragment
import app.coinverse.priceGraph.PriceFragment
import app.coinverse.user.PermissionsDialogFragment
import app.coinverse.user.SignInDialogFragment
import app.coinverse.user.models.User
import app.coinverse.utils.*
import app.coinverse.utils.livedata.EventObserver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions.circleCropTransform
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import kotlinx.android.synthetic.main.fragment_home.*
import java.util.*

private val LOG_TAG = HomeFragment::class.java.simpleName

class HomeFragment : Fragment() {

    private var user: FirebaseUser? = null
    private var isAppBarExpanded = false
    private var isSavedContentExpanded = false

    private lateinit var binding: FragmentHomeBinding
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_LOC_PERMISSION ->
                if ((grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED)) getLocation()
                else homeViewModel.location.value = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(USER_KEY, user)
        outState.putBoolean(APP_BAR_EXPANDED_KEY, isAppBarExpanded)
        outState.putBoolean(SAVED_CONTENT_EXPANDED_KEY, isSavedContentExpanded)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(APP_BAR_EXPANDED_KEY)) appBar.setExpanded(true)
            else appBar.setExpanded(false)
            if (savedInstanceState.getBoolean(SAVED_CONTENT_EXPANDED_KEY)) {
                swipeToRefresh.isEnabled = false
                bottomSheetBehavior.state = STATE_EXPANDED
                setBottomSheetExpanded()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeViewModel = ViewModelProviders.of(activity!!).get(HomeViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.setLifecycleOwner(this)
        binding.viewmodel = homeViewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        user = homeViewModel.getCurrentUser()
        initProfileButton(user != null)
        initCollapsingToolbarStates()
        observeSignIn(savedInstanceState)
        initSavedBottomSheetContainer(savedInstanceState)
        setClickListeners()
        initSwipeToRefresh()
        observeSavedContentSelected()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState == null
                && childFragmentManager.findFragmentByTag(PRICEGRAPH_FRAGMENT_TAG) == null
                && childFragmentManager.findFragmentByTag(CONTENT_FEED_FRAGMENT_TAG) == null) {
            childFragmentManager.beginTransaction()
                    .replace(priceContainer.id, PriceFragment.newInstance(), PRICEGRAPH_FRAGMENT_TAG)
                    .commit()
            childFragmentManager.beginTransaction().replace(contentContainer.id,
                    ContentFragment.newInstance(Bundle().apply {
                        putString(FEED_TYPE_KEY, MAIN.name)
                    }), CONTENT_FEED_FRAGMENT_TAG)
                    .commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                user = homeViewModel.getCurrentUser()
                initProfileButton(user != null)
            } else println(String.format("sign_in fail:%s", response?.error?.errorCode))
        }
    }

    private fun initCollapsingToolbarStates() {
        appBar.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
            override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
                // appBar expanded.
                if (Math.abs(verticalOffset) - appBarLayout.totalScrollRange != 0) {
                    isAppBarExpanded = true
                    swipeToRefresh.isEnabled = true
                    bottomSheetBehavior.isHideable = true
                    bottomSheetBehavior.state = STATE_HIDDEN
                } else { // appBar collapsed.
                    isAppBarExpanded = false
                    swipeToRefresh.isEnabled = false
                    if (bottomSheetBehavior.state == STATE_HIDDEN) {
                        bottomSheetBehavior.isHideable = false
                        bottomSheetBehavior.state = STATE_COLLAPSED
                    }
                }
            }
        })
    }

    private fun initProfileButton(isLoggedIn: Boolean) {
        if (isLoggedIn) Glide.with(this).load(user?.photoUrl.toString())
                .apply(circleCropTransform()).into(profileButton)
        else profileButton.setImageResource(ic_astronaut_color_accent_24dp)
    }

    private fun initSavedBottomSheetContainer(savedInstanceState: Bundle?) {
        bottomSheetBehavior = from(bottomSheet)
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.peekHeight = SAVED_BOTTOM_SHEET_PEEK_HEIGHT
        bottomSheet.layoutParams.height = getDisplayHeight(context!!)
        if (savedInstanceState == null && homeViewModel.user.value == null)
            childFragmentManager.beginTransaction().replace(
                    R.id.savedContentContainer,
                    SignInDialogFragment.newInstance(Bundle().apply {
                        putInt(SIGNIN_TYPE_KEY, FULLSCREEN.code)
                    }))
                    .commit()
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_EXPANDED) {
                    homeViewModel.bottomSheetState.value = STATE_EXPANDED
                    setBottomSheetExpanded()
                }
                if (newState == STATE_COLLAPSED) {
                    isSavedContentExpanded = false
                    appBar.visibility = VISIBLE
                    bottom_handle.visibility = VISIBLE
                    bottom_handle_elevation.visibility = VISIBLE
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        observeBottomSheetBackPressed()
    }

    private fun setBottomSheetExpanded() {
        isSavedContentExpanded = true
        appBar.visibility = GONE
        bottom_handle.visibility = GONE
        bottom_handle_elevation.visibility = GONE
    }

    private fun observeBottomSheetBackPressed() {
        homeViewModel.bottomSheetState.observe(viewLifecycleOwner, Observer { bottomSheetState ->
            if (bottomSheetState == STATE_COLLAPSED) bottomSheetBehavior.state = STATE_COLLAPSED
            if (bottomSheetState == STATE_HIDDEN) {
                bottomSheetBehavior.isHideable = true
                bottomSheetBehavior.state = STATE_HIDDEN
            }
        })
    }

    private fun setClickListeners() {
        profileButton.setOnClickListener { view: View ->
            if (user != null) view.findNavController().navigate(R.id.action_homeFragment_to_profileFragment,
                    actionHomeFragmentToProfileFragment(user!!).apply { user = user }.arguments)
            else SignInDialogFragment.newInstance(Bundle().apply { putInt(SIGNIN_TYPE_KEY, DIALOG.ordinal) })
                    .show(childFragmentManager, SIGNIN_DIALOG_FRAGMENT_TAG)
        }
        submitBugButton.setOnClickListener { view: View ->
            Intent(ACTION_SENDTO).let { intent ->
                intent.data = Uri.parse(getString(mail_to))
                intent.putExtra(EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                        .putExtra(EXTRA_SUBJECT, "${SUPPORT_SUBJECT} $VERSION_NAME")
                        .putExtra(EXTRA_TEXT, "${SUPPORT_BODY} " +
                                "${SUPPORT_ISSUE}" +
                                "${SUPPORT_VERSION} $VERSION_NAME" +
                                "${SUPPORT_ANDROID_API} $SDK_INT" +
                                "${SUPPORT_DEVICE} ${BRAND.substring(0, 1).toUpperCase() + BRAND.substring(1)}, $MODEL" +
                                "${SUPPORT_USER + if (user != null) user!!.uid else getString(logged_out)}")
                if (intent.resolveActivity(activity?.packageManager) != null) startActivity(intent)
            }
        }
    }

    private fun observeSignIn(savedInstanceState: Bundle?) {
        homeViewModel.user.observe(this, Observer { user: FirebaseUser? ->
            this.user = user
            initProfileButton(user != null)
            if (user != null) { // Signed in.
                //TODO: 1) Move to repository 2) Replace with Firestore security rule.
                usersCollection.document(user.uid).get().addOnCompleteListener { userQuery ->
                    // Create user.
                    if (!userQuery.result!!.exists()) {
                        usersCollection.document(user.uid).set(
                                User(user.uid, user.displayName, user.email, user.phoneNumber,
                                        user.photoUrl.toString(),
                                        Timestamp(Date(user.metadata!!.creationTimestamp)),
                                        Timestamp(Date(user.metadata!!.lastSignInTimestamp)),
                                        user.providerId, FREE, READ,
                                        0.0, 0.0, 0.0,
                                        0.0, 0.0, 0.0,
                                        0.0, 0.0))
                                .addOnSuccessListener {
                                    Log.v(LOG_TAG, String.format("New user added success:%s", it))
                                }.addOnFailureListener {
                                    Log.v(LOG_TAG, String.format("New user added failure:%s", it))
                                }
                    }
                }
                if (savedInstanceState == null || savedInstanceState.getParcelable<FirebaseUser>(USER_KEY) == null) {
                    initMainContent()
                    initSavedContentFragment()
                }
            } else if (savedInstanceState == null)  /*Signed out.*/ initMainContent()
        })
    }

    private fun initMainContent() {
        if (homeViewModel.accountType.value == FREE) getLocationPermissionCheck()
        (childFragmentManager.findFragmentById(R.id.contentContainer) as ContentFragment)
                .initMainContent(false)
    }

    private fun initSavedContentFragment() {
        childFragmentManager.beginTransaction().replace(
                savedContentContainer.id,
                ContentFragment.newInstance(Bundle().apply { putString(FEED_TYPE_KEY, SAVED.name) }),
                SAVED_CONTENT_TAG).commit()
    }

    private fun getLocationPermissionCheck() {
        if (checkSelfPermission(activity!!, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED) getLocation()
        else {
            val pref = activity?.getPreferences(MODE_PRIVATE) ?: return
            if (pref.getBoolean(getString(first_open), true)
                    || shouldShowRequestPermissionRationale(activity!!, ACCESS_COARSE_LOCATION)) {
                with(pref.edit()) {
                    putBoolean(getString(first_open), false)
                    apply()
                }
                PermissionsDialogFragment.newInstance().show(childFragmentManager, null)
                homeViewModel.showLocationPermission.observe(viewLifecycleOwner, EventObserver { showPermission ->
                    if (showPermission) requestPermissions(arrayOf(ACCESS_COARSE_LOCATION), REQUEST_CODE_LOC_PERMISSION)
                })
            } else requestPermissions(arrayOf(ACCESS_COARSE_LOCATION), REQUEST_CODE_LOC_PERMISSION)
        }
    }

    // Permission checked above.
    @SuppressLint("MissingPermission")
    private fun getLocation() {
        LocationServices.getFusedLocationProviderClient(activity!!).lastLocation.addOnSuccessListener { location: Location? ->
            homeViewModel.location.value =
                    activity?.getPreferences(MODE_PRIVATE)?.let { pref ->
                        if (location != null) {
                            pref.edit().apply {
                                putFloat(getString(user_lat), location.latitude.toFloat())
                                putFloat(getString(user_lng), location.longitude.toFloat())
                                apply()
                            }
                            location
                        } else
                            Location("").apply {
                                latitude = pref.getFloat(getString(user_lat), DEFAULT_LAT.toFloat()).toDouble()
                                longitude = pref.getFloat(getString(user_lng), DEFAULT_LNG.toFloat()).toDouble()

                            }
                    }
        }
    }

    private fun initSwipeToRefresh() {
        homeViewModel.isSwipeToRefreshEnabled.observe(viewLifecycleOwner, Observer { isEnabled: Boolean ->
            swipeToRefresh.isEnabled = isEnabled
        })
        homeViewModel.isRefreshing.observe(viewLifecycleOwner, Observer { isRefreshing: Boolean ->
            swipeToRefresh.isRefreshing = isRefreshing
        })
        swipeToRefresh.setOnRefreshListener {
            (childFragmentManager.findFragmentById(R.id.priceContainer) as PriceFragment)
                    .getPrices(false, false)
            initMainContent()
        }
    }

    private fun observeSavedContentSelected() {
        homeViewModel.savedContentSelected.observe(viewLifecycleOwner, EventObserver { content ->
            if (childFragmentManager.findFragmentByTag(CONTENT_DIALOG_FRAGMENT_TAG) == null)
                ContentDialogFragment()
                        .newInstance(Bundle().apply { putParcelable(CONTENT_KEY, content) })
                        .show(childFragmentManager, CONTENT_DIALOG_FRAGMENT_TAG)
        })
    }
}