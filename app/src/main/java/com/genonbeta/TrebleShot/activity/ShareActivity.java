package com.genonbeta.TrebleShot.activity;

import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.genonbeta.CoolSocket.CoolCommunication;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.app.GActivity;
import com.genonbeta.TrebleShot.config.AppConfig;
import com.genonbeta.TrebleShot.database.Transaction;
import com.genonbeta.TrebleShot.fragment.NetworkDeviceListFragment;
import com.genonbeta.TrebleShot.helper.ApplicationHelper;
import com.genonbeta.TrebleShot.helper.AwaitedFileSender;
import com.genonbeta.TrebleShot.helper.FileUtils;
import com.genonbeta.TrebleShot.helper.JsonResponseHandler;
import com.genonbeta.TrebleShot.helper.NetworkDevice;
import com.genonbeta.TrebleShot.service.CommunicationService;
import com.genonbeta.TrebleShot.service.Keyword;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.Socket;
import java.util.ArrayList;

public class ShareActivity extends GActivity
{
	public static final String TAG = "ShareActivity";

	public static final int REQUEST_CODE_EDIT_BOX = 1;

	public static final String ACTION_SEND = "genonbeta.intent.action.TREBLESHOT_SEND";
	public static final String ACTION_SEND_MULTIPLE = "genonbeta.intent.action.TREBLESHOT_SEND_MULTIPLE";

	public static final String EXTRA_FILENAME_LIST = "extraFileNames";

	private EditText mStatusText;
	private Transaction mTransaction;
	private NetworkDeviceListFragment mDeviceListFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_share);

		mTransaction = new Transaction(getApplicationContext());
		mDeviceListFragment = (NetworkDeviceListFragment) getSupportFragmentManager().findFragmentById(R.id.activity_share_fragment);
		mStatusText = (EditText) findViewById(R.id.activity_share_info_text);

		if (getIntent() != null && getIntent().getAction() != null)
		{
			String action = getIntent().getAction();

			switch (action)
			{
				case ACTION_SEND:
				case Intent.ACTION_SEND:
					if (getIntent().hasExtra(Intent.EXTRA_TEXT))
					{
						appendStatusText(getIntent().getStringExtra(Intent.EXTRA_TEXT));

						ImageView editButton = (ImageView) findViewById(R.id.activity_share_edit_button);

						editButton.setVisibility(View.VISIBLE);
						editButton.setOnClickListener(new View.OnClickListener()
						{
							@Override
							public void onClick(View view)
							{
								startActivityForResult(new Intent(ShareActivity.this, TextEditorActivity.class)
										.setAction(TextEditorActivity.ACTION_EDIT_TEXT)
										.putExtra(TextEditorActivity.EXTRA_TEXT_INDEX, mStatusText.getText().toString()), REQUEST_CODE_EDIT_BOX);
							}
						});

						mDeviceListFragment.setOnListClickListener(new AdapterView.OnItemClickListener()
						{
							@Override
							public void onItemClick(AdapterView<?> parent, View view, int position, long id)
							{
								final NetworkDevice device = (NetworkDevice) mDeviceListFragment.getListAdapter().getItem(position);
								final String deviceIp = device.ip;

								CoolCommunication.Messenger.send(deviceIp, AppConfig.COMMUNATION_SERVER_PORT, null,
										new JsonResponseHandler()
										{
											@Override
											public void onJsonMessage(Socket socket, CoolCommunication.Messenger.Process process, JSONObject json)
											{
												try
												{
													json.put(Keyword.REQUEST, Keyword.REQUEST_CLIPBOARD);
													json.put(Keyword.CLIPBOARD_TEXT, mStatusText.getText().toString());

													JSONObject response = new JSONObject(process.waitForResponse());

													if (response.getBoolean(Keyword.RESULT))
													{

													}
													else
														showToast(getString(R.string.file_sending_error_msg, getString(R.string.not_allowed_error)));
												} catch (JSONException e)
												{
													showToast(getString(R.string.file_sending_error_msg, getString(R.string.communication_problem)));
												}
											}

											@Override
											public void onError(Exception e)
											{
												e.printStackTrace();
												showToast(getString(R.string.file_sending_error_msg, getString(R.string.connection_problem)));
											}
										}
								);
							}
						});
					}
					else
					{
						final ArrayList<Uri> fileUris = new ArrayList<>();
						final ArrayList<CharSequence> fileNames = new ArrayList<>();
						final Uri fileUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
						final File file = ApplicationHelper.getFileFromUri(getApplicationContext(), fileUri);

						fileUris.add(fileUri);

						if (getIntent().hasExtra(EXTRA_FILENAME_LIST))
						{
							String fileName = getIntent().getStringExtra(EXTRA_FILENAME_LIST);

							fileNames.add(fileName);
							appendStatusText(fileName);
						}
						else
							appendStatusText(file.getName());

						mDeviceListFragment.setOnListClickListener(new AdapterView.OnItemClickListener()
						{
							@Override
							public void onItemClick(AdapterView<?> parent, View view, int position, long id)
							{
								final NetworkDevice device = (NetworkDevice) mDeviceListFragment.getListAdapter().getItem(position);
								final String deviceIp = device.ip;

								handleFiles(fileUris, fileNames.size() > 0 ? fileNames : null, deviceIp);
							}
						});

					}
					break;
				case ACTION_SEND_MULTIPLE:
				case Intent.ACTION_SEND_MULTIPLE:
					final ArrayList<Uri> fileUris = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
					final ArrayList<CharSequence> fileNames = getIntent().hasExtra(EXTRA_FILENAME_LIST) ? getIntent().getCharSequenceArrayListExtra(EXTRA_FILENAME_LIST) : null;

					appendStatusText(getString(R.string.item_selected, String.valueOf(fileUris.size())));

					mDeviceListFragment.setOnListClickListener(new AdapterView.OnItemClickListener()
					{
						@Override
						public void onItemClick(AdapterView<?> parent, View view, int position, long id)
						{
							final NetworkDevice device = (NetworkDevice) mDeviceListFragment.getListAdapter().getItem(position);
							final String deviceIp = device.ip;

							handleFiles(fileUris, fileNames, deviceIp);
						}
					});

					break;
				default:
					Toast.makeText(this, R.string.type_not_supported_msg, Toast.LENGTH_SHORT).show();
					finish();
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK)
			if (requestCode == REQUEST_CODE_EDIT_BOX && data != null && data.hasExtra(TextEditorActivity.EXTRA_TEXT_INDEX))
				appendStatusText(data.getStringExtra(TextEditorActivity.EXTRA_TEXT_INDEX));
	}

	protected void appendStatusText(CharSequence charSequence)
	{
		mStatusText.getText().clear();
		mStatusText.getText().append(charSequence);
	}

	protected void handleFiles(final ArrayList<Uri> fileUris, final ArrayList<CharSequence> fileNames, final String deviceIp)
	{
		CoolCommunication.Messenger.send(deviceIp, AppConfig.COMMUNATION_SERVER_PORT, null,
				new JsonResponseHandler()
				{
					@Override
					public void onConfigure(CoolCommunication.Messenger.Process process)
					{
						process.setSocketTimeout(AppConfig.DEFAULT_SOCKET_LARGE_TIMEOUT);
					}

					@Override
					public void onJsonMessage(Socket socket, com.genonbeta.CoolSocket.CoolCommunication.Messenger.Process process, JSONObject json)
					{
						JSONArray filesArray = new JSONArray();

						try
						{
							int index = 0;
							int acceptId = ApplicationHelper.getUniqueNumber();

							json.put(Keyword.REQUEST, Keyword.REQUEST_TRANSFER);
							json.put(Keyword.ACCEPT_ID, acceptId);

							for (Uri fileUri : fileUris)
							{
								File file = ApplicationHelper.getFileFromUri(getApplicationContext(), fileUri);
								String fileName = String.valueOf(fileNames == null ? file.getName() : fileNames.get(index));

								if (file == null)
									continue;

								if (file.isFile())
								{
									int requestId = ApplicationHelper.getUniqueNumber();
									AwaitedFileSender sender = new AwaitedFileSender(requestId, acceptId, deviceIp, fileName, 0, file);
									JSONObject thisJson = new JSONObject();

									try
									{
										String fileMime = FileUtils.getFileContentType(file.getAbsolutePath());

										thisJson.put(Keyword.FILE_NAME, fileName);
										thisJson.put(Keyword.FILE_SIZE, file.length());
										thisJson.put(Keyword.REQUEST_ID, requestId);
										thisJson.put(Keyword.FILE_MIME, fileMime);

										filesArray.put(thisJson);

										mTransaction.registerTransaction(sender);
									} catch (Exception e)
									{
										Log.e(TAG, "Sender error on file: " + e.getClass().getName() + " : " + file.getName());
									}
								}

								index++;
							}

							json.put(Keyword.FILES_INDEX, filesArray);

							JSONObject response = new JSONObject(process.waitForResponse());

							if (!response.getBoolean(Keyword.RESULT))
							{
								Log.d(TAG, "Keyword did not accept the request remove pre-added senders");
								mTransaction.removeTransactionGroup(acceptId);

								showToast(getString(R.string.file_sending_error_msg, getString(R.string.not_allowed_error)));
							}
						} catch (JSONException e)
						{
							showToast(getString(R.string.file_sending_error_msg, getString(R.string.communication_problem)));
						}
					}

					@Override
					public void onError(Exception e)
					{
						showToast(getString(R.string.file_sending_error_msg, getString(R.string.connection_problem)));
					}
				}
		);
	}

	protected void showToast(String msg)
	{
		Looper.prepare();
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
		Looper.loop();
	}
}
