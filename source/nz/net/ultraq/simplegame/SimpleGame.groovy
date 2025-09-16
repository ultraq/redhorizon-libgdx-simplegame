/*
 * Copyright 2025, Emanuel Rabina (http://www.ultraq.net.nz/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nz.net.ultraq.simplegame

import nz.net.ultraq.redhorizon.audio.AudioDevice
import nz.net.ultraq.redhorizon.audio.Sound
import nz.net.ultraq.redhorizon.audio.openal.OpenALAudioDevice
import nz.net.ultraq.redhorizon.graphics.Image
import nz.net.ultraq.redhorizon.graphics.Shader
import nz.net.ultraq.redhorizon.graphics.Window
import nz.net.ultraq.redhorizon.graphics.opengl.BasicShader
import nz.net.ultraq.redhorizon.graphics.opengl.OpenGLWindow
import nz.net.ultraq.redhorizon.input.CursorPositionEvent
import nz.net.ultraq.redhorizon.input.InputEvent
import nz.net.ultraq.redhorizon.input.KeyEvent
import nz.net.ultraq.redhorizon.input.MouseButtonEvent

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.primitives.Rectanglef
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import static org.lwjgl.glfw.GLFW.*

import java.util.concurrent.LinkedBlockingQueue

/**
 * Entry point to the simple game example.
 *
 * @author Emanuel Rabina
 */
@Command(name = 'libgdx-simple-game')
class SimpleGame implements Runnable {

	static {
		System.setProperty('org.lwjgl.system.stackSize', '20480')
	}

	static void main(String[] args) {
		System.exit(new CommandLine(new SimpleGame()).execute(args))
	}

	private static final Logger logger = LoggerFactory.getLogger(SimpleGame)
	private static final float worldWidth = 800f
	private static final float worldHeight = 500f
	private static final Matrix4f projection = new Matrix4f().setOrthoSymmetric(worldWidth, worldHeight, 0, 1)
	private static final Matrix4f view = new Matrix4f().setLookAt(
		(float)(worldWidth / 2), (float)(worldHeight / 2), 1,
		(float)(worldWidth / 2), (float)(worldHeight / 2), 0,
		0, 1, 0
	)
	private static final float BUCKET_SPEED = 400f
	private static final float DROP_SPEED = 200f

	private Window window
	private Image backgroundImage
	private Image bucketImage
	private Shader shader
	private final List<Image> drops = []
	private AudioDevice device
	// TODO: A proper streaming music type: https://github.com/ultraq/redhorizon/issues/56#issuecomment-3289393917
	private Sound music
	private Sound dropSound

	private final Queue<InputEvent> inputEventsQueue = new LinkedBlockingQueue<>()
	private final List<InputEvent> inputEvents = []
	private boolean movingLeft
	private boolean movingRight
	private boolean moveToCursor
	private Vector3f screenCursorPosition = new Vector3f()
	private Vector3f bucketPosition = new Vector3f()
	private Vector3f lastBucketPosition = new Vector3f()
	private float dropTimer
	private Vector3f dropPosition = new Vector3f()
	private Rectanglef bucketHitBox = new Rectanglef()
	private Rectanglef dropHitBox = new Rectanglef()

	@Override
	void run() {

		try {
			window = new OpenGLWindow(800, 500, 'libGDX Simple Game')
				.withVSync(true)
				.on(InputEvent) { event ->
					if (event instanceof KeyEvent && event.keyPressed(GLFW_KEY_ESCAPE)) {
						window.shouldClose(true)
					}
					else {
						inputEventsQueue << event
					}
				}
			shader = new BasicShader()
			backgroundImage = new Image('background.png', getResourceAsStream('nz/net/ultraq/simplegame/background.png'))
			bucketImage = new Image('bucket.png', getResourceAsStream('nz/net/ultraq/simplegame/bucket.png'))

			device = new OpenALAudioDevice()
				.withMasterVolume(0.5)
			music = new Sound('music.mp3', getResourceAsStream('nz/net/ultraq/simplegame/music.mp3'))
			dropSound = new Sound('drop.mp3', getResourceAsStream('nz/net/ultraq/simplegame/drop.mp3'))

			window.show()
			music.play()
			var lastUpdateTimeMs = System.currentTimeMillis()

			while (!window.shouldClose()) {
				var currentTimeMs = System.currentTimeMillis()
				var delta = (currentTimeMs - lastUpdateTimeMs) / 1000 as float

				input(delta)
				logic(delta)
				render()

				lastUpdateTimeMs = currentTimeMs
				Thread.yield()
			}
		}
		finally {
			dropSound?.close()
			music?.close()
			device?.close()
			drops*.close()
			bucketImage?.close()
			backgroundImage?.close()
			shader?.close()
			window?.close()
		}
	}

	/**
	 * Process input events.
	 */
	private void input(float delta) {

		// TODO: Handle input press/release in some sort of system that can be
		//       easily queried, https://github.com/ultraq/redhorizon/issues/56#issuecomment-3289393917
		inputEventsQueue.drainTo(inputEvents)
		inputEvents.each { event ->
			if (event instanceof KeyEvent) {
				if (event.keyPressed(GLFW_KEY_LEFT)) {
					movingLeft = true
				}
				else if (event.keyReleased(GLFW_KEY_LEFT)) {
					movingLeft = false
				}
				else if (event.keyPressed(GLFW_KEY_RIGHT)) {
					movingRight = true
				}
				else if (event.keyReleased(GLFW_KEY_RIGHT)) {
					movingRight = false
				}
			}
			else if (event instanceof CursorPositionEvent) {
				screenCursorPosition.set(event.xPos, event.yPos, 0)
			}
			else if (event instanceof MouseButtonEvent) {
				if (event.buttonPressed(GLFW_MOUSE_BUTTON_LEFT)) {
					moveToCursor = true
				}
				else if (event.buttonReleased(GLFW_MOUSE_BUTTON_LEFT)) {
					moveToCursor = false
				}
			}
		}
		inputEvents.clear()

		if (movingLeft) {
			bucketImage.transform.translate((float)(-BUCKET_SPEED * delta), 0, 0)
		}
		if (movingRight) {
			bucketImage.transform.translate((float)(BUCKET_SPEED * delta), 0, 0)
		}
		if (moveToCursor) {
			bucketImage.transform.translate((float)(screenCursorPosition.x - bucketPosition.x - (bucketImage.width / 2)), 0, 0)
		}
	}

	/**
	 * Perform the game logic.
	 */
	private void logic(float delta) {

		// Clamp the bucket to the screen
		bucketImage.transform.getTranslation(bucketPosition)
		if (bucketPosition.x < 0) {
			bucketImage.transform.translation(0, 0, 0)
			bucketImage.transform.getTranslation(bucketPosition)
		}
		else if (bucketPosition.x > worldWidth - bucketImage.width) {
			bucketImage.transform.translation((float)(worldWidth - bucketImage.width), 0, 0)
			bucketImage.transform.getTranslation(bucketPosition)
		}

		if (bucketPosition != lastBucketPosition) {
			logger.debug('Bucket position: {}', bucketPosition.x)
			lastBucketPosition.set(bucketPosition)
		}
		bucketHitBox.set(bucketPosition.x, bucketPosition.y, (float)(bucketPosition.x + bucketImage.width), (float)(bucketPosition.y + bucketImage.height))

		// Create a new drop every 1 second
		dropTimer += delta
		if (dropTimer > 1) {
			// TODO: Some object that can reuse an existing image, instead of having
			//       to load a new one every time: https://github.com/ultraq/redhorizon/issues/56#issuecomment-3289393917
			var drop = new Image('drop.png', getResourceAsStream('nz/net/ultraq/simplegame/drop.png'))
			drop.transform.translation((float)(Math.random() * (worldWidth - drop.width)), 500, 0)
			drops << drop
			dropTimer -= 1
		}

		for (var iterator = drops.listIterator(); iterator.hasNext(); ) {
			var drop = iterator.next()

			// Move drops down the screen
			drop.transform.translate(0, (float)(-DROP_SPEED * delta), 0)
			drop.transform.getTranslation(dropPosition)
			dropHitBox.set(dropPosition.x, dropPosition.y, (float)(dropPosition.x + drop.width), (float)(dropPosition.y + drop.height))

			// Check if this drop has been collected by the bucket
			if (bucketHitBox.intersectsRectangle(dropHitBox)) {
				logger.info('Drop collected!')
				dropSound.play()
				iterator.remove()
				drop.close()
			}

			// Check if the drop is no longer visible
			else if (drop.transform.getTranslation(dropPosition).y < -drop.height) {
				logger.info('Drop no longer visible')
				iterator.remove()
				drop.close()
			}
		}
	}

	/**
	 * Draw the game objects to the screen.
	 */
	private void render() {

		window.withFrame { ->
			shader.use()
			shader.setUniform('projection', projection)
			shader.setUniform('view', view)
			backgroundImage.draw(shader)
			bucketImage.draw(shader)
			drops*.draw(shader)
		}
	}
}
