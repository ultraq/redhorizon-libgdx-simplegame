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

import nz.net.ultraq.redhorizon.graphics.Image
import nz.net.ultraq.redhorizon.graphics.Shader
import nz.net.ultraq.redhorizon.graphics.Window
import nz.net.ultraq.redhorizon.graphics.opengl.BasicShader
import nz.net.ultraq.redhorizon.graphics.opengl.OpenGLWindow
import nz.net.ultraq.redhorizon.input.KeyEvent

import org.joml.Matrix4f
import picocli.CommandLine
import picocli.CommandLine.Command
import static org.lwjgl.glfw.GLFW.*

/**
 * Entry point to the simple game example.
 *
 * @author Emanuel Rabina
 */
@Command(name = 'libgdx-simple-game')
class SimpleGame implements Runnable {

	static {
		System.setProperty('org.lwjgl.system.stackSize', '10240')
	}

	static void main(String[] args) {
		System.exit(new CommandLine(new SimpleGame()).execute(args))
	}

	private Window window
	private Image backgroundImage
	private Image bucketImage
	private Image dropImage
	private Shader shader

	@Override
	void run() {

		try {
			window = new OpenGLWindow(800, 500, 'libGDX Simple Game')
				.withVSync(true)
				.on(KeyEvent) { event ->
					if (event.keyPressed(GLFW_KEY_ESCAPE)) {
						window.shouldClose(true)
					}
				}
			shader = new BasicShader()
			backgroundImage = new Image('background.png', getResourceAsStream('nz/net/ultraq/simplegame/background.png'))
			bucketImage = new Image('bucket.png', getResourceAsStream('nz/net/ultraq/simplegame/bucket.png'))
			dropImage = new Image('drop.png', getResourceAsStream('nz/net/ultraq/simplegame/drop.png'))

			var projection = new Matrix4f().setOrthoSymmetric(800, 500, 0, 1)
			var view = new Matrix4f().setLookAt(
				400, 250, 1,
				400, 250, 0,
				0, 1, 0
			)

			window.show()
			while (!window.shouldClose()) {
				window.withFrame { ->
					shader.use()
					shader.setUniform('projection', projection)
					shader.setUniform('view', view)
					backgroundImage.draw(shader)
					bucketImage.draw(shader)
				}
				Thread.yield()
			}
		}
		finally {
			dropImage?.close()
			bucketImage?.close()
			backgroundImage?.close()
			window?.close()
		}
	}
}
