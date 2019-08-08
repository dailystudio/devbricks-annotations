package com.dailystudio.annotation.example

import android.os.Bundle
import com.dailystudio.app.activity.ActionBarFragmentActivity

class MainActivity : ActionBarFragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

    }

}