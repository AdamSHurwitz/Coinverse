package app.coinverse.user

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.findNavController
import app.coinverse.App
import app.coinverse.R
import app.coinverse.R.string.already_signed_out
import app.coinverse.R.string.deleted
import app.coinverse.R.string.error_sign_in_anonymously
import app.coinverse.R.string.signed_out
import app.coinverse.R.string.unable_to_delete
import app.coinverse.analytics.Analytics
import app.coinverse.databinding.FragmentUserBinding
import app.coinverse.firebase.firebaseApp
import app.coinverse.home.HomeViewModel
import app.coinverse.user.viewmodel.UserViewModel
import app.coinverse.user.viewmodel.UserViewModelFactory
import app.coinverse.utils.FeedType.DISMISSED
import app.coinverse.utils.PROFILE_VIEW
import app.coinverse.utils.Status.SUCCESS
import app.coinverse.utils.setImageUrlCircle
import app.coinverse.utils.snackbarWithText
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.AuthUI.getInstance
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.google.android.material.snackbar.Snackbar.make
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_user.*
import kotlinx.android.synthetic.main.toolbar_app.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private val LOG_TAG = UserFragment::class.java.simpleName

/**
 * TODO: Refactor
 *  1. Refactor with Unidirectional Data Flow. See [app.coinverse.feed.FeedViewModel].
 *  https://medium.com/hackernoon/android-unidirectional-flow-with-livedata-bf24119e747
 *  2. Move Firebase calls to Repository.
 **/

class UserFragment : Fragment() {
    @Inject
    lateinit var analytics: Analytics
    @Inject
    lateinit var userRepository: UserRepository

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val userViewModel: UserViewModel by activityViewModels {
        UserViewModelFactory(owner = this, repository = userRepository)
    }

    private lateinit var binding: FragmentUserBinding
    private lateinit var user: FirebaseUser

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context.applicationContext as App).component.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics.setCurrentScreen(requireActivity(), PROFILE_VIEW)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentUserBinding.inflate(inflater, container, false)
        user = UserFragmentArgs.fromBundle(requireArguments()).user
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setToolbar()
        profieImage.setImageUrlCircle(requireContext(), user.photoUrl.toString())
        setClickListeners()
    }

    fun setToolbar() {
        toolbar.title = user.displayName
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    fun setClickListeners() {
        dismissedContent.setOnClickListener { view: View ->
            view.findNavController().navigate(R.id.action_userFragment_to_dismissedContentFragment,
                    UserFragmentDirections.actionUserFragmentToDismissedContentFragment().apply {
                        feedType = DISMISSED.name
                    }.arguments)
        }

        signOut.setOnClickListener { view: View ->
            var message: Int
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                try {
                    lifecycleScope.launch {
                        getInstance().signOut(requireContext()).await()
                        homeViewModel.setUser(null)
                        message = signed_out
                        FirebaseAuth.getInstance(firebaseApp(true)).signInAnonymously().await()
                        Snackbar.make(view, getString(message), LENGTH_SHORT).show()
                        activity?.onBackPressed()
                    }
                } catch (exception: FirebaseAuthException) {
                    //TODO: Add retry.
                    snackbarWithText(
                        resources,
                        getString(error_sign_in_anonymously),
                        contentContainer
                    )
                    Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
                    FirebaseCrashlytics.getInstance()
                        .log("$LOG_TAG observeSignIn ${exception.localizedMessage}")
                }
            } else {
                message = already_signed_out
                Snackbar.make(view, getString(message), LENGTH_SHORT).show()
            }
        }
        delete.setOnClickListener { view: View ->
            FirebaseAuth.getInstance().currentUser?.let { user ->
                userViewModel.deleteUser(user).observe(viewLifecycleOwner) { status ->
                    if (status == SUCCESS)
                        lifecycleScope.launch {
                            try {
                                AuthUI.getInstance().signOut(requireContext()).await()
                                homeViewModel.setUser(null)
                                Snackbar.make(view, getString(deleted), LENGTH_SHORT).show()
                                activity?.onBackPressed()
                                FirebaseAuth.getInstance(firebaseApp(true)).signInAnonymously()
                                    .await()
                                Log.v(LOG_TAG, "observeSignIn anonymous success")
                            } catch (e: FirebaseAuthException) {
                                snackbarWithText(
                                    resources,
                                    getString(error_sign_in_anonymously),
                                    contentContainer
                                )
                                Toast.makeText(
                                    context,
                                    "Authentication failed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                FirebaseCrashlytics.getInstance()
                                    .log("$LOG_TAG observeSignIn ${e.localizedMessage}")
                            }
                        }
                    else make(view, getString(unable_to_delete), LENGTH_SHORT).show()
                }
            }
        }
    }
}
